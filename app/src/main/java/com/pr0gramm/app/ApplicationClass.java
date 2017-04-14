package com.pr0gramm.app;

import android.app.Application;
import android.content.Context;
import android.os.StrictMode;

import com.crashlytics.android.Crashlytics;
import com.evernote.android.job.JobManager;
import com.f2prateek.dart.Dart;
import com.github.salomonbrys.kodein.Kodein;
import com.github.salomonbrys.kodein.KodeinAware;
import com.google.android.gms.ads.MobileAds;
import com.pr0gramm.app.services.ThemeHelper;
import com.pr0gramm.app.sync.SyncJob;
import com.pr0gramm.app.ui.ActivityErrorHandler;
import com.pr0gramm.app.util.CrashlyticsLogHandler;
import com.pr0gramm.app.util.Lazy;
import com.pr0gramm.app.util.LooperScheduler;
import com.thefinestartist.Base;

import net.danlew.android.joda.JodaTimeAndroid;

import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.TimeUnit;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogManager;

import butterknife.ButterKnife;
import io.fabric.sdk.android.Fabric;
import pl.brightinventions.slf4android.LogLevel;
import pl.brightinventions.slf4android.LoggerConfiguration;
import rx.Scheduler;
import rx.android.plugins.RxAndroidPlugins;
import rx.android.plugins.RxAndroidSchedulersHook;

import static com.pr0gramm.app.ui.dialogs.ErrorDialogFragment.setGlobalErrorDialogHandler;
import static com.pr0gramm.app.util.AndroidUtility.buildVersionCode;

/**
 * Global application class for pr0gramm app.
 */
public class ApplicationClass extends Application implements KodeinAware {
    private static final Logger logger = LoggerFactory.getLogger("Pr0grammApplication");

    final Lazy<AppComponent> appComponent = Lazy.of(() -> DaggerAppComponent.builder()
            .appModule(new AppModule(this))
            .httpModule(new HttpModule())
            .build());

    private static ApplicationClass INSTANCE;

    public ApplicationClass() {
        INSTANCE = this;
    }

    @Override
    public void onCreate() {
        super.onCreate();

        Stats.INSTANCE.init(buildVersionCode());
        JodaTimeAndroid.init(this);
        Base.initialize(this);

        Settings.Companion.initialize(this);

        // do job handling & scheduling
        JobManager jobManager = JobManager.create(this);
        jobManager.getConfig().setVerbose(true);
        jobManager.addJobCreator(SyncJob.Companion.getCREATOR());

        // schedule first sync 30seconds after bootup.
        SyncJob.Companion.scheduleNextSyncIn(30, TimeUnit.SECONDS);

        if (BuildConfig.DEBUG) {
            logger.info("This is a development version.");
            StrictMode.enableDefaults();
            ButterKnife.setDebug(true);
            Dart.setDebug(true);

        } else {
            // allow all the dirty stuff.
            StrictMode.setVmPolicy(StrictMode.VmPolicy.LAX);
            StrictMode.setThreadPolicy(StrictMode.ThreadPolicy.LAX);

            logger.info("Initialize fabric");
            Fabric.with(this, new Crashlytics());

            LoggerConfiguration.configuration()
                    .removeRootLogcatHandler()
                    .setRootLogLevel(LogLevel.INFO)
                    .addHandlerToRootLogger(new CrashlyticsLogHandler());
        }

        // initialize this to show errors always in the context of the current activity.
        setGlobalErrorDialogHandler(new ActivityErrorHandler(this));

        // set as global constant that we can access from everywhere
        appComponent().googleAnalytics();

        Dagger.initEagerSingletons(this);

        if (BuildConfig.DEBUG) {
            StethoWrapper.INSTANCE.init(this);
        }

        // get the correct theme for the app!
        ThemeHelper.updateTheme(this);

        // disable verbose logging
        java.util.logging.Logger log = LogManager.getLogManager().getLogger("");
        if (log != null) {
            for (Handler h : log.getHandlers()) {
                h.setLevel(Level.INFO);
            }
        }

        // enable ads.
        MobileAds.initialize(this, "ca-app-pub-2308657767126505~4138045673");
        MobileAds.setAppVolume(0);
        MobileAds.setAppMuted(true);
    }

    public static ApplicationClass get(Context context) {
        return (ApplicationClass) context.getApplicationContext();
    }

    public static AppComponent appComponent() {
        return INSTANCE.appComponent.get();
    }

    static {
        RxAndroidPlugins.getInstance().registerSchedulersHook(new RxAndroidSchedulersHook() {
            @Override
            public Scheduler getMainThreadScheduler() {
                return LooperScheduler.MAIN;
            }
        });
    }

    @NotNull
    @Override
    public Kodein getKodein() {
        return new KApp(this).getKodein();
    }
}
