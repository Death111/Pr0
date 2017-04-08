package com.pr0gramm.app.util;

import android.content.Context;
import android.content.pm.PackageManager;
import android.content.pm.PermissionInfo;
import android.support.annotation.Nullable;
import android.support.annotation.StringRes;

import com.google.common.base.Predicate;
import com.google.common.base.Predicates;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.gson.JsonSyntaxException;
import com.google.gson.stream.MalformedJsonException;
import com.pr0gramm.app.R;
import com.pr0gramm.app.api.pr0gramm.HttpErrorException;
import com.pr0gramm.app.api.pr0gramm.LoginCookieHandler;
import com.pr0gramm.app.ui.PermissionHelper;

import java.io.EOFException;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.ConnectException;
import java.net.ProtocolException;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeoutException;

import javax.net.ssl.SSLException;

import rx.functions.Func2;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.primitives.Ints.asList;

/**
 * This provides utilities for formatting of exceptions..
 */
public class ErrorFormatting {
    public static Formatter<?> getFormatter(Throwable error) {
        for (Formatter<?> formatter : FORMATTERS) {
            if (formatter.handles(error))
                return formatter;
        }

        throw new IllegalStateException("There should always be a default formatter");
    }

    public static class Formatter<T extends Throwable> {
        private final Class<? extends T> errorType;
        private final Predicate<T> check;
        private final Func2<T, Context, String> message;
        private boolean report = true;

        Formatter(Class<? extends T> errorType, Func2<T, Context, String> message) {
            this(errorType, Predicates.alwaysTrue(), message);
        }

        Formatter(Class<? extends T> errorType, @StringRes int message) {
            this(errorType, Predicates.alwaysTrue(), (err, ctx) -> ctx.getString(message));
        }

        Formatter(Class<? extends T> errorType, Predicate<T> check) {
            this(errorType, check, (err, ctx) -> null);
        }

        Formatter(Class<? extends T> errorType, Predicate<T> check, @StringRes int message) {
            this(errorType, check, (err, ctx) -> ctx.getString(message));
        }

        Formatter(Class<? extends T> errorType, Predicate<T> check, Func2<T, Context, String> message) {
            this.errorType = errorType;
            this.check = check;
            this.message = message;
        }

        /**
         * Tests if this formatter handles the given exception.
         */
        public boolean handles(Throwable thr) {
            //noinspection unchecked
            return errorType.isInstance(thr) && check.apply((T) thr);
        }

        /**
         * Gets the message for the given exception. You must only call this,
         * if {@link #handles(Throwable)} returned true before.
         */
        @SuppressWarnings("unchecked")
        public String getMessage(Context context, Throwable thr) {
            return message.call((T) thr, context);
        }

        /**
         * Deactivates logging of this kind of error.
         *
         * @return this instance.
         */
        Formatter<T> doNotReport() {
            report = false;
            return this;
        }

        /**
         * Returns true, if this exception should be logged
         */
        public boolean shouldSendToCrashlytics() {
            return report;
        }
    }

    private static class RetrofitStatusFormatter extends Formatter<Exception> {
        @SuppressWarnings("SimplifiableIfStatement")
        RetrofitStatusFormatter(Predicate<HttpErrorException> check, @StringRes int message) {
            super(Exception.class, err -> {
                if (err instanceof retrofit2.HttpException)
                    return check.apply(HttpErrorException.Companion.from((retrofit2.HttpException) err));

                if (err instanceof HttpErrorException)
                    return check.apply((HttpErrorException) err);

                return false;
            }, message);
        }
    }

    /**
     * Returns a list containing multiple error formatters in the order they should
     * be applied.
     *
     * @return The error formatters.
     */
    @SuppressWarnings("ThrowableResultOfMethodCallIgnored")
    private static List<Formatter<?>> makeErrorFormatters() {
        final List<Formatter<?>> formatters = new ArrayList<>();

        final int error_exception_of_type = R.string.error_exception_of_type;
        Func2<Throwable, Context, String> guessMessage = (err, context) -> {
            String message = err.getLocalizedMessage();
            if (Strings.isNullOrEmpty(message))
                message = err.getMessage();

            if (Strings.isNullOrEmpty(message)) {
                message = context.getString(error_exception_of_type, err.getClass().getSimpleName());
            }

            return message;
        };

        formatters.add(new RetrofitStatusFormatter(
                err -> err.getCode() == 403 && err.getErrorBody().contains("cloudflare"),
                R.string.error_cloudflare).doNotReport());

        formatters.add(new RetrofitStatusFormatter(
                err -> err.getCode() == 403 && err.getErrorBody().contains("<html>"),
                R.string.error_blocked).doNotReport());

        formatters.add(new RetrofitStatusFormatter(
                err -> asList(401, 403).contains(err.getCode()),
                R.string.error_not_authorized).doNotReport());

        formatters.add(new RetrofitStatusFormatter(
                err -> err.getCode() == 429,
                R.string.error_rate_limited).doNotReport());

        formatters.add(new RetrofitStatusFormatter(
                err -> err.getCode() == 404,
                R.string.error_not_found).doNotReport());

        formatters.add(new RetrofitStatusFormatter(
                err -> err.getCode() == 504,
                R.string.error_proxy_timeout).doNotReport());

        formatters.add(new RetrofitStatusFormatter(
                err -> err.getCode() == 522,
                R.string.error_origin_timeout_ddos).doNotReport());

        formatters.add(new RetrofitStatusFormatter(
                err -> err.getCode() / 100 == 5,
                R.string.error_service_unavailable).doNotReport());

        formatters.add(new Formatter<>(JsonSyntaxException.class,
                R.string.error_json));

        formatters.add(new Formatter<>(Throwable.class,
                err -> hasCause(err, FileNotFoundException.class),
                R.string.error_post_not_found).doNotReport());

        formatters.add(new Formatter<>(Throwable.class,
                err -> hasCause(err, TimeoutException.class),
                R.string.error_timeout).doNotReport());

        formatters.add(new Formatter<>(Throwable.class,
                err -> hasCause(err, SocketTimeoutException.class),
                R.string.error_timeout).doNotReport());

        formatters.add(new Formatter<>(Throwable.class,
                err -> hasCause(err, MalformedJsonException.class),
                R.string.error_conversion).doNotReport());

        formatters.add(new Formatter<>(Throwable.class,
                err -> hasCause(err, UnknownHostException.class),
                R.string.error_host_not_found).doNotReport());

        formatters.add(new Formatter<>(Throwable.class,
                err -> hasCause(err, SSLException.class),
                R.string.error_ssl_error).doNotReport());

        formatters.add(new Formatter<>(Throwable.class,
                err -> hasCause(err, ProtocolException.class),
                R.string.error_protocol_exception));

        final int error_connect_exception_https = R.string.error_connect_exception_https;
        final int error_connect_exception = R.string.error_connect_exception;
        formatters.add(new Formatter<>(Throwable.class,
                err -> hasCause(err, ConnectException.class),
                (err, context) -> {
                    ConnectException error = checkNotNull(getCause(err, ConnectException.class));
                    if (error.toString().contains(":443")) {
                        return context.getString(error_connect_exception_https,
                                String.valueOf(err.getLocalizedMessage()));
                    } else {
                        return context.getString(error_connect_exception,
                                String.valueOf(err.getLocalizedMessage()));
                    }
                }).doNotReport());

        formatters.add(new Formatter<>(Throwable.class,
                err -> hasCause(err, SocketException.class),
                R.string.error_socket).doNotReport());

        formatters.add(new Formatter<>(Throwable.class,
                err -> hasCause(err, EOFException.class),
                R.string.error_socket).doNotReport());

        formatters.add(new Formatter<>(LoginCookieHandler.LoginRequiredException.class,
                R.string.error_login_required_exception));

        formatters.add(new Formatter<>(IllegalStateException.class,
                err -> String.valueOf(err).contains("onSaveInstanceState")).doNotReport());

        final int error_json_mapping = R.string.error_json_mapping;
        formatters.add(new Formatter<>(IllegalStateException.class,
                err -> String.valueOf(err).contains(": Expected "),
                (err, context) -> context.getString(error_json_mapping, err.getMessage())).doNotReport());

        final int error_permission_not_granted = R.string.error_permission_not_granted;
        formatters.add(new Formatter<>(PermissionHelper.PermissionNotGranted.class,
                (error, context) -> {
                    CharSequence permissionName = error.getPermission();
                    try {
                        PackageManager packageManager = context.getPackageManager();
                        PermissionInfo permissionInfo = packageManager.getPermissionInfo(
                                error.getPermission(), 0);
                        permissionName = permissionInfo.loadLabel(packageManager);
                    } catch (PackageManager.NameNotFoundException ignored) {
                    }

                    return context.getString(error_permission_not_granted, permissionName);
                }));

        // add a default formatter for io exceptions, but do not log them
        formatters.add(new Formatter<>(IOException.class, guessMessage).doNotReport());

        // oops
        formatters.add(new Formatter<>(NullPointerException.class, R.string.error_nullpointer));

        // no memory, this is bad!
        formatters.add(new Formatter<>(OutOfMemoryError.class, R.string.error_oom));

        // add a default formatter.
        formatters.add(new Formatter<>(Throwable.class, guessMessage));

        return formatters;
    }

    @Nullable
    private static <T extends Throwable> T getCause(Throwable thr, Class<T> causeClass) {
        while (thr != null) {
            if (causeClass.isInstance(thr)) {
                return causeClass.cast(thr);
            }

            thr = thr.getCause();
        }

        return null;
    }

    /**
     * Checks if the given throwable or any of it's causes is of the given type.
     */
    private static boolean hasCause(Throwable thr, Class<? extends Throwable> causeClass) {
        return getCause(thr, causeClass) != null;
    }

    private static final ImmutableList<Formatter<?>> FORMATTERS = ImmutableList.copyOf(makeErrorFormatters());
}
