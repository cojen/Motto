
module org.cojen.motto {
    exports org.cojen.motto.model;

    requires org.cojen.maker;

    // For checking if value classes are supported.
    requires java.management;
}
