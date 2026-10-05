package com.mindharbor.common;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mindharbor.modules.admin.mapper.AdminOperationLogMapper;
import com.mindharbor.modules.admin.service.impl.AdminOperationLogServiceImpl;
import com.mindharbor.modules.chat.mapper.ChatMessageMapper;
import com.mindharbor.modules.chat.service.impl.ChatServiceImpl;
import com.mindharbor.modules.feedback.mapper.FeedbackMapper;
import com.mindharbor.modules.feedback.service.impl.FeedbackServiceImpl;
import com.mindharbor.modules.notification.mapper.NotificationMapper;
import com.mindharbor.modules.notification.service.impl.NotificationServiceImpl;
import com.mindharbor.modules.relax.mapper.RelaxRecordMapper;
import com.mindharbor.modules.relax.mapper.StressLogMapper;
import com.mindharbor.modules.relax.service.impl.RelaxServiceImpl;
import com.mindharbor.modules.user.mapper.UserMapper;
import com.mindharbor.modules.user.service.impl.UserServiceImpl;
import com.mindharbor.security.CurrentUserProvider;
import java.lang.reflect.Proxy;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 分页 offset 的整型溢出防线。
 *
 * <p>{@code safePage} 只有下界（{@code Math.max(page, 0)}）没有上界，而 {@code safePage * safeSize}
 * 是 <b>int × int</b>。一旦页码越过 {@code Integer.MAX_VALUE / size}（最大页长 50 时即 42,949,673），
 * 乘法先溢出成<b>负数</b>，再当作 offset 落到 SQL 的 {@code LIMIT/OFFSET} 上——请求必然失败。
 * 页长 100 的管理端用户列表门槛更低，{@code page ≥ 21,474,837} 就触发。
 *
 * <p>修法是让乘法以 {@code long} 参与：offset 先转 {@code long} 再乘，mapper 侧参数同步声明为
 * {@code long}。对正常页码取值逐字节不变，只在越界区间把「负数 offset」变成「一个真实但为空的结果」。
 *
 * <p>本类逐条钉住这一点：把实现改回 {@code safePage * safeSize}（int 乘法），对应用例立即变红。
 * {@code UserServiceImpl} 那条走的是内存 {@code subList}，溢出后是负下标，直接抛
 * {@code IndexOutOfBoundsException}。
 */
@DisplayName("分页 offset 整型溢出")
class PaginationOffsetOverflowTest {

    private static final int HUGE_PAGE = Integer.MAX_VALUE;

    private static final int SIZE = 50;

    private static final long USER_ID = 7L;

    @Test
    @DisplayName("放松记录：offset 以 long 参与乘法，超大页码不溢出成负数")
    void relaxRecordsOffsetStaysPositive() {
        OffsetCapture capture = new OffsetCapture();
        RelaxServiceImpl service = new RelaxServiceImpl(
                fakeMapper(RelaxRecordMapper.class, capture), null, currentUser(USER_ID));

        service.records(HUGE_PAGE, SIZE, null);

        capture.assertNoOverflow();
    }

    @Test
    @DisplayName("压力日志：offset 以 long 参与乘法，超大页码不溢出成负数")
    void stressLogsOffsetStaysPositive() {
        OffsetCapture capture = new OffsetCapture();
        RelaxServiceImpl service = new RelaxServiceImpl(
                null, fakeMapper(StressLogMapper.class, capture), currentUser(USER_ID));

        service.stressLogs(HUGE_PAGE, SIZE);

        capture.assertNoOverflow();
    }

    @Test
    @DisplayName("用户反馈：offset 以 long 参与乘法，超大页码不溢出成负数")
    void feedbackListOffsetStaysPositive() {
        OffsetCapture capture = new OffsetCapture();
        FeedbackServiceImpl service = new FeedbackServiceImpl(
                fakeMapper(FeedbackMapper.class, capture), null, null);

        service.list(HUGE_PAGE, SIZE, null);

        capture.assertNoOverflow();
    }

    @Test
    @DisplayName("操作日志：offset 以 long 参与乘法，超大页码不溢出成负数")
    void adminOperationLogOffsetStaysPositive() {
        OffsetCapture capture = new OffsetCapture();
        AdminOperationLogServiceImpl service = new AdminOperationLogServiceImpl(
                fakeMapper(AdminOperationLogMapper.class, capture), null, null);

        service.list(HUGE_PAGE, SIZE, null, (LocalDate) null, null);

        capture.assertNoOverflow();
    }

    @Test
    @DisplayName("收藏消息：offset 以 long 参与乘法，超大页码不溢出成负数")
    void favoriteMessagesOffsetStaysPositive() {
        OffsetCapture capture = new OffsetCapture();
        ChatServiceImpl service = new ChatServiceImpl(
                null,
                fakeMapper(ChatMessageMapper.class, capture),
                null,
                null,
                null,
                currentUser(USER_ID),
                null,
                null);

        service.favoriteMessages(HUGE_PAGE, SIZE, null, null);

        capture.assertNoOverflow();
    }

    @Test
    @DisplayName("通知列表：offset 以 long 参与乘法，超大页码不溢出成负数")
    void notificationListOffsetStaysPositive() {
        OffsetCapture capture = new OffsetCapture();
        NotificationServiceImpl service = new NotificationServiceImpl(
                fakeMapper(NotificationMapper.class, capture), null, null, null, currentUser(USER_ID));

        service.list(HUGE_PAGE, SIZE);

        capture.assertNoOverflow();
    }

    @Test
    @DisplayName("通知管理列表：offset 以 long 参与乘法，超大页码不溢出成负数")
    void notificationAdminListOffsetStaysPositive() {
        OffsetCapture capture = new OffsetCapture();
        NotificationServiceImpl service = new NotificationServiceImpl(
                fakeMapper(NotificationMapper.class, capture), null, null, null, null);

        service.adminList(HUGE_PAGE, SIZE);

        capture.assertNoOverflow();
    }

    @Test
    @DisplayName("普通页码：offset 仍等于 page × size，修正不得改变正常取值")
    void normalPageOffsetUnchanged() {
        OffsetCapture capture = new OffsetCapture();
        RelaxServiceImpl service = new RelaxServiceImpl(
                fakeMapper(RelaxRecordMapper.class, capture), null, currentUser(USER_ID));

        service.records(3, 20, null);

        assertEquals(20, capture.limit, "第二页起 limit 仍是每页条数");
        assertEquals(60L, capture.offset, "第 3 页、每页 20 条 ⇒ offset 必须是 60");
    }

    @Test
    @DisplayName("管理端用户列表：内存分页的起始下标不得溢出成负数")
    void adminUserListDoesNotIndexNegative() {
        UserServiceImpl service = new UserServiceImpl(
                fakeMapper(UserMapper.class, new OffsetCapture()), null, null, null, null, null);

        assertDoesNotThrow(
                () -> service.listUsers(HUGE_PAGE, 100, null),
                "safePage * safeSize 溢出成负数后，subList 会抛 IndexOutOfBoundsException");
    }

    /** 记录 mapper 收到的 limit / offset，两者都取自方法末两位参数（项目约定：limit 在前、offset 在最后）。 */
    private static final class OffsetCapture {

        private Integer limit;

        private Long offset;

        void record(Object[] args) {
            if (args == null || args.length < 2) {
                return;
            }
            if (args[args.length - 2] instanceof Integer && args[args.length - 1] instanceof Long) {
                this.limit = (Integer) args[args.length - 2];
                this.offset = (Long) args[args.length - 1];
            }
        }

        void assertNoOverflow() {
            assertTrue(offset != null, "分页查询没有把 offset 传给 mapper");
            assertTrue(offset > 0,
                    "offset 溢出成了负数，落到 SQL 的 LIMIT/OFFSET 上必然失败");
            assertEquals((long) HUGE_PAGE * limit, offset,
                    "offset 必须以 long 参与乘法；写成 int 乘法时 "
                            + HUGE_PAGE + " × " + limit + " 会先溢出再取负");
        }
    }

    /**
     * 只满足被测路径需要的假 mapper：计数返回 0、列表返回空、并顺带记录 offset。
     * 其余方法一律抛异常，避免「实现变了却悄悄通过」。
     */
    private static <T> T fakeMapper(Class<T> type, OffsetCapture capture) {
        return type.cast(Proxy.newProxyInstance(
                PaginationOffsetOverflowTest.class.getClassLoader(),
                new Class<?>[] {type},
                (proxy, method, args) -> {
                    String name = method.getName();
                    if ("toString".equals(name)) {
                        return "fake-" + type.getSimpleName();
                    }
                    if ("hashCode".equals(name)) {
                        return System.identityHashCode(proxy);
                    }
                    if ("equals".equals(name)) {
                        return proxy == args[0];
                    }
                    if (name.startsWith("count")) {
                        return 0L;
                    }
                    capture.record(args);
                    if (List.class.isAssignableFrom(method.getReturnType())) {
                        return List.of();
                    }
                    throw new UnsupportedOperationException(name);
                }));
    }

    private static CurrentUserProvider currentUser(long userId) {
        return new CurrentUserProvider() {
            @Override
            public Long getUserId() {
                return userId;
            }
        };
    }
}
