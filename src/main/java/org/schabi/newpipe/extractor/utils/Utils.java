package org.schabi.newpipe.extractor.utils;

public class Utils {
    private Utils() {
        //no instance
    }

    /**
     * Remove all non-digit characters from a string.<p>
     * Examples:<br/>
     * <ul><li>1 234 567 views -> 1234567</li>
     * <li>$31,133.124 -> 31133124</li></ul>
     *
     * @param toRemove string to remove non-digit chars
     * @return a string that contains only digits
     */
    public static String removeNonDigitCharacters(String toRemove) {
        return toRemove.replaceAll("\\D+", "");
    }

    /**
     * Check if throwable have the cause
     */
    public static boolean hasCauseThrowable(Throwable throwable, Class<?>... causesToCheck) {
        // Check if getCause is not the same as cause (the getCause is already the root),
        // as it will cause a infinite loop if it is
        Throwable cause, getCause = throwable;

        for (Class<?> causesEl : causesToCheck) {
            if (throwable.getClass().isAssignableFrom(causesEl)) {
                return true;
            }
        }

        while ((cause = throwable.getCause()) != null && getCause != cause) {
            getCause = cause;
            for (Class<?> causesEl : causesToCheck) {
                if (cause.getClass().isAssignableFrom(causesEl)) {
                    return true;
                }
            }
        }
        return false;
    }
}
