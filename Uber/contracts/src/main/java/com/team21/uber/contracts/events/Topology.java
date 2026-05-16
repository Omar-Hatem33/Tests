package com.team21.uber.contracts.events;

// RabbitMQ topology constants. One topic exchange per producer service.
public final class Topology {
    private Topology() {}

    public static final String USER_EVENTS     = "user.events";
    public static final String DRIVER_EVENTS   = "driver.events";
    public static final String RIDE_EVENTS     = "ride.events";
    public static final String LOCATION_EVENTS = "location.events";
    public static final String PAYMENT_EVENTS  = "payment.events";

    public static final String RK_USER_REGISTERED     = "user.registered";
    public static final String RK_USER_DEACTIVATED    = "user.deactivated";
    public static final String RK_DRIVER_STATUS       = "driver.status-changed";
    public static final String RK_DRIVER_RATED        = "driver.rated";
    public static final String RK_DRIVER_DOC_VERIFIED = "driver.document.verified";
    public static final String RK_RIDE_PLACED         = "ride.placed";
    public static final String RK_RIDE_COMPLETED      = "ride.completed";
    public static final String RK_RIDE_CANCELLED      = "ride.cancelled";
    public static final String RK_LOCATION_TRACKED    = "location.tracked";
    public static final String RK_PAYMENT_INITIATED   = "payment.initiated";
    public static final String RK_PAYMENT_COMPLETED   = "payment.completed";
    public static final String RK_PAYMENT_FAILED      = "payment.failed";
    public static final String RK_PAYMENT_REFUNDED    = "payment.refunded";

    public static final String DLX_SUFFIX = ".dlx";
    public static final String DLQ_SUFFIX = ".dlq";
}
