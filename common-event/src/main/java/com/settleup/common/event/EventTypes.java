package com.settleup.common.event;

/**
 * Event types. Catalog: docs/event-design-v0.1.md §3
 */
public final class EventTypes {

    private EventTypes() {}

    public static final String ORDER_CREATED = "OrderCreated";
    public static final String ORDER_CONFIRMED = "OrderConfirmed";
    public static final String ORDER_CANCEL_REQUESTED = "OrderCancelRequested";
    public static final String ORDER_CANCELLED = "OrderCancelled";
    public static final String PAYMENT_APPROVED = "PaymentApproved";
    public static final String PAYMENT_FAILED = "PaymentFailed";
    public static final String PAYMENT_CANCELLED = "PaymentCancelled";
}
