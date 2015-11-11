package net.sf.inflater.processor.internal;

final class Utils {
    public static String explain(Throwable error) {
        return error.getMessage() != null ? error.getMessage() : error.getClass().getName();
    }
}
