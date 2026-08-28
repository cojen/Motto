
module org.cojen.motto {
    exports motto;
    exports org.cojen.motto.model;
    exports org.cojen.motto.runtime;

    requires org.cojen.maker;

    // For checking if value classes are supported.
    requires java.management;
}
