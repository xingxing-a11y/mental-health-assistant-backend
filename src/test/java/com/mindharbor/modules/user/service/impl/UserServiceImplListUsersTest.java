package com.mindharbor.modules.user.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mindharbor.common.PageResult;
import com.mindharbor.modules.user.entity.User;
import com.mindharbor.modules.user.mapper.UserMapper;
import com.mindharbor.modules.user.vo.UserVO;
import java.lang.reflect.Proxy;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 管理端用户列表：分页必须落在数据库，不能在内存里切。
 *
 * <p>原实现是 {@code selectList} 全表捞进内存后 {@code subList}——每翻一页都重复一次全表扫描，
 * 十万级用户时一次就是几十 MB 进堆，2C2G 机器上几个并发请求即可 OOM。改成 {@code count} +
 * {@code LIMIT/OFFSET} 之后，这里逐条钉住四件事：offset 以 long 参与乘法、越界页不打库、
 * 空关键词不拼 LIKE、总数字段仍然正确。
 *
 * <p>把实现改回 {@code selectList} + {@code subList}，本类的分页用例立即变红。
 */
@DisplayName("管理端用户列表分页")
class UserServiceImplListUsersTest {

    @Test
    @DisplayName("第 3 页、每页 20 条 ⇒ offset 以 long 传给 SQL，总数与页数正确")
    void offsetIsComputedAsLong() {
        FakeUserMapper mapper = new FakeUserMapper(100);

        PageResult<UserVO> result = service(mapper).listUsers(2, 20, null);

        assertEquals(40L, mapper.lastOffset, "page 从 0 起：第 3 页、每页 20 条 ⇒ offset 必须是 40");
        assertEquals(20, mapper.lastLimit);
        assertEquals(100L, result.getTotal());
        assertEquals(5, result.getTotalPages());
        assertEquals(20, result.getItems().size());
    }

    @Test
    @DisplayName("超大页码：offset 不得溢出成负数，越界页直接返回空且不打库")
    void hugePageSkipsQuery() {
        FakeUserMapper mapper = new FakeUserMapper(100);

        PageResult<UserVO> result = service(mapper).listUsers(Integer.MAX_VALUE, 100, null);

        assertNull(mapper.lastOffset, "越界页应直接返回空，不应再发起注定为空的查询");
        assertTrue(result.getItems().isEmpty());
        assertEquals(100L, result.getTotal(), "越界页仍要回正确总数，否则前端算不出页数");
    }

    @Test
    @DisplayName("空白关键词收敛成 null：SQL 不带 LIKE 条件")
    void blankKeywordBecomesNull() {
        FakeUserMapper mapper = new FakeUserMapper(10);

        service(mapper).listUsers(0, 10, "   ");

        assertNull(mapper.lastKeyword, "关键词只有空白时应传 null，由 SQL 跳过 LIKE");
    }

    @Test
    @DisplayName("关键词超过 100 字符按 100 截断，不把超长串拼进 LIKE")
    void keywordIsTruncated() {
        FakeUserMapper mapper = new FakeUserMapper(10);

        service(mapper).listUsers(0, 10, "x".repeat(200));

        assertEquals(100, mapper.lastKeyword.length());
    }

    @Test
    @DisplayName("页长越界被夹到 1~100：size=0 取 1，size=999 取 100")
    void sizeIsClamped() {
        FakeUserMapper zero = new FakeUserMapper(1000);
        service(zero).listUsers(0, 0, null);
        assertEquals(1, zero.lastLimit, "size 下界为 1，否则 LIMIT 0 永远查不到数据");

        FakeUserMapper huge = new FakeUserMapper(1000);
        service(huge).listUsers(0, 999, null);
        assertEquals(100, huge.lastLimit, "size 上界为 100，避免一次拉走整页数据");
    }

    private static UserServiceImpl service(FakeUserMapper mapper) {
        return new UserServiceImpl(mapper.asMapper(), null, null, null, null, null);
    }

    /**
     * 只实现被测路径用到的两个方法：{@code countByNickname} 与 {@code pageByNickname}。
     * 其余方法一律抛异常，避免实现换了以后测试仍悄悄通过。
     */
    private static final class FakeUserMapper {

        private final long total;

        private String lastKeyword;

        private Integer lastLimit;

        private Long lastOffset;

        FakeUserMapper(long total) {
            this.total = total;
        }

        UserMapper asMapper() {
            return (UserMapper) Proxy.newProxyInstance(
                    UserServiceImplListUsersTest.class.getClassLoader(),
                    new Class<?>[] {UserMapper.class},
                    (proxy, method, args) -> switch (method.getName()) {
                        case "countByNickname" -> {
                            lastKeyword = (String) args[0];
                            yield total;
                        }
                        case "pageByNickname" -> {
                            lastKeyword = (String) args[0];
                            lastLimit = (Integer) args[1];
                            lastOffset = (Long) args[2];
                            int remaining = (int) Math.max(total - lastOffset, 0);
                            List<User> users = new ArrayList<>();
                            for (int i = 0; i < Math.min(lastLimit, remaining); i++) {
                                users.add(user(lastOffset + i));
                            }
                            yield users;
                        }
                        case "toString" -> "fake-UserMapper";
                        case "hashCode" -> System.identityHashCode(proxy);
                        case "equals" -> proxy == args[0];
                        default -> throw new UnsupportedOperationException(method.getName());
                    });
        }

        private static User user(long id) {
            User user = new User();
            user.setId(id);
            user.setUsername("user" + id);
            user.setNickname("昵称" + id);
            user.setRole(User.Role.USER);
            user.setStatus(User.Status.ACTIVE);
            user.setCreatedAt(Instant.now());
            return user;
        }
    }
}
