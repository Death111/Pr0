package com.pr0gramm.app.ui.fragments;

import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import com.google.common.base.Optional;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableList;
import com.pr0gramm.app.DialogBuilder;
import com.pr0gramm.app.Graph;
import com.pr0gramm.app.GraphDrawable;
import com.pr0gramm.app.R;
import com.pr0gramm.app.RxRoboFragment;
import com.pr0gramm.app.Settings;
import com.pr0gramm.app.Track;
import com.pr0gramm.app.UserClasses;
import com.pr0gramm.app.WrapContentLinearLayoutManager;
import com.pr0gramm.app.api.pr0gramm.response.Info;
import com.pr0gramm.app.feed.FeedFilter;
import com.pr0gramm.app.feed.FeedType;
import com.pr0gramm.app.orm.Bookmark;
import com.pr0gramm.app.services.BookmarkService;
import com.pr0gramm.app.services.InboxService;
import com.pr0gramm.app.services.SingleShotService;
import com.pr0gramm.app.services.UserService;
import com.pr0gramm.app.ui.FeedbackActivity;
import com.pr0gramm.app.ui.InboxActivity;
import com.pr0gramm.app.ui.InboxType;
import com.pr0gramm.app.ui.SettingsActivity;
import com.pr0gramm.app.ui.UploadActivity;
import com.pr0gramm.app.ui.dialogs.ErrorDialogFragment;
import com.pr0gramm.app.ui.dialogs.LoginActivity;
import com.pr0gramm.app.ui.dialogs.LogoutDialogFragment;

import java.util.ArrayList;
import java.util.List;

import javax.inject.Inject;

import roboguice.inject.InjectResource;
import roboguice.inject.InjectView;
import rx.Observable;
import rx.functions.Action1;
import rx.functions.Actions;

import static com.google.common.base.Objects.equal;
import static com.pr0gramm.app.AndroidUtility.getStatusBarHeight;
import static com.pr0gramm.app.AndroidUtility.ifPresent;
import static rx.android.app.AppObservable.bindSupportFragment;
import static rx.android.lifecycle.LifecycleObservable.bindFragmentLifecycle;

/**
 */
public class DrawerFragment extends RxRoboFragment {
    @Inject
    private UserService userService;

    @Inject
    private InboxService inboxService;

    @Inject
    private Settings settings;

    @Inject
    private BookmarkService bookmarkService;

    @Inject
    private SingleShotService singleShotService;

    @InjectView(R.id.username)
    private TextView usernameView;

    @InjectView(R.id.user_type)
    private TextView userTypeView;

    @InjectView(R.id.kpi_benis)
    private TextView benisView;

    @InjectView(R.id.benis_delta)
    private TextView benisDeltaView;

    @InjectView(R.id.benis_container)
    private View benisContainer;

    @InjectView(R.id.benis_graph)
    private ImageView benisGraph;

    @InjectView(R.id.action_login)
    private View loginView;

    @InjectView(R.id.action_logout)
    private View logoutView;

    @InjectView(R.id.action_feedback)
    private TextView feedbackView;

    @InjectView(R.id.action_settings)
    private View settingsView;

    @InjectView(R.id.user_image)
    private View userImageView;

    @InjectView(R.id.drawer_nav_list)
    private RecyclerView navItemsRecyclerView;

    @InjectResource(R.drawable.ic_black_action_favorite)
    private Drawable iconFavorites;

    @InjectResource(R.drawable.ic_black_action_home)
    private Drawable iconFeedTypePromoted;

    @InjectResource(R.drawable.ic_black_action_stelz)
    private Drawable iconFeedTypePremium;

    @InjectResource(R.drawable.ic_black_action_trending)
    private Drawable iconFeedTypeNew;

    @InjectResource(R.drawable.ic_action_random)
    private Drawable iconFeedTypeRandom;

    @InjectResource(R.drawable.ic_category_controversial)
    private Drawable iconFeedTypeControversial;

    @InjectResource(R.drawable.ic_black_action_bookmark)
    private Drawable iconBookmark;

    @InjectResource(R.drawable.ic_action_email)
    private Drawable iconInbox;

    @InjectResource(R.drawable.ic_black_action_upload)
    private Drawable iconUpload;

    private final NavigationAdapter navigationAdapter = new NavigationAdapter();

    private static final int ICON_ALPHA = 127;
    private final ColorStateList defaultColor = ColorStateList.valueOf(Color.BLACK);
    private ColorStateList markedColor;

    private final LoginActivity.DoIfAuthorizedHelper doIfAuthorizedHelper = LoginActivity.helper(this);

    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.left_drawer, container, false);
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // get "marked" color
        int primary = getActivity().getResources().getColor(R.color.primary);
        markedColor = ColorStateList.valueOf(primary);

        // add some space on the top for the translucent status bar
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            ViewGroup.MarginLayoutParams params = (ViewGroup.MarginLayoutParams)
                    userImageView.getLayoutParams();

            params.topMargin += getStatusBarHeight(getActivity());
        }

        // initialize the top navigation items
        navItemsRecyclerView.setAdapter(navigationAdapter);
        navItemsRecyclerView.setLayoutManager(new WrapContentLinearLayoutManager(
                getActivity(), WrapContentLinearLayoutManager.VERTICAL, false));

        // add the static items to the navigation
        navigationAdapter.setNavigationItems(getFixedNavigationItems(Optional.<Info.User>absent()));

        settingsView.setOnClickListener(v -> {
            Intent intent = new Intent(getActivity(), SettingsActivity.class);
            startActivity(intent);
        });

        loginView.setOnClickListener(v -> {
            Intent intent = new Intent(getActivity(), LoginActivity.class);
            startActivity(intent);
        });

        logoutView.setOnClickListener(v -> {
            LogoutDialogFragment fragment = new LogoutDialogFragment();
            fragment.show(getFragmentManager(), null);
        });

        benisGraph.setOnClickListener(this::onBenisGraphClicked);

        changeCompoundDrawableColor(feedbackView, defaultColor.withAlpha(ICON_ALPHA));

        feedbackView.setOnClickListener(v -> {
            Intent intent = new Intent(getActivity(), FeedbackActivity.class);
            startActivity(intent);
        });
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        doIfAuthorizedHelper.onActivityResult(requestCode, resultCode);
    }

    /**
     * Adds the default "fixed" items to the menu
     */
    private List<NavigationItem> getFixedNavigationItems(Optional<Info.User> userInfo) {
        List<NavigationItem> items = new ArrayList<>();
        items.add(new NavigationItem(
                new FeedFilter().withFeedType(FeedType.PROMOTED),
                getString(R.string.action_feed_type_promoted),
                iconFeedTypePromoted));

        items.add(new NavigationItem(
                new FeedFilter().withFeedType(FeedType.NEW),
                getString(R.string.action_feed_type_new),
                iconFeedTypeNew));

        items.add(new NavigationItem(
                new FeedFilter().withFeedType(FeedType.CONTROVERSIAL),
                getString(R.string.action_feed_type_controversial),
                iconFeedTypeControversial));

        if (settings.showCategoryRandom()) {
            items.add(new NavigationItem(
                    new FeedFilter().withFeedType(FeedType.RANDOM),
                    getString(R.string.action_feed_type_random),
                    iconFeedTypeRandom));
        }

        if (userService.isPremiumUser()) {
            items.add(new NavigationItem(
                    new FeedFilter().withFeedType(FeedType.PREMIUM),
                    getString(R.string.action_feed_type_premium),
                    iconFeedTypePremium));
        }

        if (userInfo.isPresent()) {
            String name = userInfo.get().getName();
            items.add(new NavigationItem(
                    new FeedFilter().withFeedType(FeedType.NEW).withLikes(name),
                    getString(R.string.action_favorites),
                    iconFavorites));

        }

        return items;
    }

    private void onBenisGraphClicked(View view) {
        DialogBuilder.start(getActivity())
                .content(R.string.benis_graph_explanation)
                .positive(R.string.okay)
                .show();
    }

    @Override
    public void onResume() {
        super.onResume();

        bind(userService.loginState())
                .subscribe(this::onLoginStateChanged, Actions.empty());

        bind(newNavigationItemsObservable()).subscribe(
                navigationAdapter::setNavigationItems,
                ErrorDialogFragment.defaultOnError());

        benisGraph.setVisibility(settings.benisGraphEnabled() ? View.VISIBLE : View.GONE);
    }

    private <T> Observable<T> bind(Observable<T> observable) {
        return bindFragmentLifecycle(lifecycle(), bindSupportFragment(this, observable));
    }

    @SuppressWarnings("unchecked")
    private Observable<List<NavigationItem>> newNavigationItemsObservable() {
        // observe and merge the menu items from different sources
        return Observable.combineLatest(ImmutableList.of(
                userService.loginState().map(st -> st.getInfo() != null
                        ? getFixedNavigationItems(Optional.of(st.getInfo().getUser()))
                        : getFixedNavigationItems(Optional.<Info.User>absent())),

                userService.loginState()
                        .flatMap(ignored -> bookmarkService.get())
                        .map(this::bookmarksToNavItem),

                inboxService.unreadMessagesCount()
                        .map(this::getInboxNavigationItem)
                        .map(ImmutableList::of),

                Observable.just(ImmutableList.of(getUploadNavigationItem()))
        ), args -> {

            ArrayList<NavigationItem> result = new ArrayList<>();
            for (Object arg : args)
                result.addAll((List<NavigationItem>) arg);

            return result;
        });
    }

    private List<NavigationItem> bookmarksToNavItem(List<Bookmark> entries) {
        if (singleShotService.isFirstTimeToday("bookmarksLoaded"))
            Track.bookmarks(entries.size());

        boolean premium = userService.isPremiumUser();
        return FluentIterable.from(entries)
                .filter(entry -> premium || entry.asFeedFilter().getFeedType() != FeedType.PREMIUM)
                .transform(entry -> {
                    Drawable icon = iconBookmark.getConstantState().newDrawable();
                    String title = entry.getTitle().toUpperCase();
                    return new NavigationItem(entry.asFeedFilter(), title, icon, entry);
                })
                .toList();
    }

    /**
     * Fakes the drawable tint by applying a color filter on all compound
     * drawables of this view.
     *
     * @param view  The view to "tint"
     * @param color The color with which the drawables are to be tinted.
     */
    private static void changeCompoundDrawableColor(TextView view, ColorStateList color) {
        int defaultColor = color.getDefaultColor();
        Drawable[] drawables = view.getCompoundDrawables();
        for (Drawable drawable : drawables) {
            if (drawable == null)
                continue;

            // fake the tint with a color filter.
            drawable.mutate().setColorFilter(defaultColor, PorterDuff.Mode.SRC_IN);
        }
    }

    private void onLoginStateChanged(UserService.LoginState state) {
        if (state.isAuthorized()) {
            Info.User user = state.getInfo().getUser();
            usernameView.setText(user.getName());
            usernameView.setOnClickListener(v -> onUsernameClicked());

            userTypeView.setVisibility(View.VISIBLE);
            userTypeView.setTextColor(getResources().getColor(UserClasses.MarkColors.get(user.getMark())));
            userTypeView.setText(getString(UserClasses.MarkStrings.get(user.getMark())).toUpperCase());

            Graph benis = state.getBenisHistory();

            benisView.setText(String.valueOf(user.getScore()));
            benisContainer.setVisibility(View.VISIBLE);
            benisGraph.setImageDrawable(new GraphDrawable(benis));

            if (benis.points().size() > 2)
                updateBenisDeltaForGraph(benis);

            loginView.setVisibility(View.GONE);
            logoutView.setVisibility(View.VISIBLE);
        } else {
            usernameView.setText(R.string.pr0gramm);
            usernameView.setOnClickListener(null);

            userTypeView.setText("");
            userTypeView.setVisibility(View.GONE);

            benisContainer.setVisibility(View.GONE);
            benisGraph.setImageDrawable(null);

            loginView.setVisibility(View.VISIBLE);
            logoutView.setVisibility(View.GONE);
        }
    }

    private void updateBenisDeltaForGraph(Graph benis) {
        int delta = (int) (benis.last().y - benis.first().y);
        if (delta == 0) {
            benisDeltaView.setVisibility(View.GONE);

        } else {
            benisDeltaView.setVisibility(View.VISIBLE);
            benisDeltaView.setTextColor(delta < 0
                    ? getResources().getColor(R.color.benis_delta_negative)
                    : getResources().getColor(R.color.benis_delta_positive));

            benisDeltaView.setText(String.format("%s%d", delta < 0 ? "↓" : "↑", delta));
        }
    }

    private void onUsernameClicked() {
        ifPresent(userService.getName(), name -> {
            FeedFilter filter = new FeedFilter()
                    .withFeedType(FeedType.NEW)
                    .withUser(name);

            onFeedFilterClicked(filter);
        });
    }

    public void updateCurrentFilters(FeedFilter current) {
        navigationAdapter.setCurrentFilter(current);
    }

    /**
     * Returns the menu item that takes the user to the inbox.
     */
    private NavigationItem getInboxNavigationItem(Integer unreadCount) {
        Runnable run = () -> {
            Intent intent = new Intent(getActivity(), InboxActivity.class);
            intent.putExtra(InboxActivity.EXTRA_INBOX_TYPE, unreadCount == 0
                    ? InboxType.ALL.ordinal()
                    : InboxType.UNREAD.ordinal());

            startActivity(intent);
        };

        return new NavigationItem(getString(R.string.action_inbox), iconInbox)
                .withClickAction(() -> doIfAuthorizedHelper.run(run, run))
                .withLayout(R.layout.left_drawer_nav_item_inbox)
                .withExtraBind(holder -> {
                    TextView unread = (TextView) holder.itemView.findViewById(R.id.unread_count);
                    unread.setText(String.valueOf(unreadCount));
                    unread.setVisibility(unreadCount > 0 ? View.VISIBLE : View.GONE);
                });
    }

    /**
     * Returns the menu item that takes the user to the upload activity.
     */
    private NavigationItem getUploadNavigationItem() {
        Runnable run = () -> startActivity(new Intent(getActivity(), UploadActivity.class));
        return new NavigationItem(getString(R.string.action_upload), iconUpload)
                .withClickAction(() -> doIfAuthorizedHelper.run(run, run));
    }

    public interface OnFeedFilterSelected {
        /**
         * Called if a drawer filter was clicked.
         *
         * @param filter The feed filter that was clicked.
         */
        void onFeedFilterSelectedInNavigation(FeedFilter filter);

        /**
         * Some other menu item was clicked and we request that this
         * drawer gets closed
         */
        void onOtherNavigationItemClicked();
    }

    private void onFeedFilterClicked(FeedFilter filter) {
        if (getActivity() instanceof OnFeedFilterSelected) {
            ((OnFeedFilterSelected) getActivity()).onFeedFilterSelectedInNavigation(filter);
        }
    }

    private void onOtherNavigationItemClicked() {
        if (getActivity() instanceof OnFeedFilterSelected) {
            ((OnFeedFilterSelected) getActivity()).onOtherNavigationItemClicked();
        }
    }

    private class NavigationAdapter extends RecyclerView.Adapter<NavigationItemViewHolder> {
        private final List<NavigationItem> allItems = new ArrayList<>();
        private Optional<NavigationItem> selected = Optional.absent();
        private FeedFilter currentFilter;

        @Override
        public NavigationItemViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(viewType, parent, false);
            return new NavigationItemViewHolder(view);
        }

        @Override
        public int getItemViewType(int position) {
            return allItems.get(position).layout;
        }

        public void onBindViewHolder(NavigationItemViewHolder holder, int position) {
            NavigationItem item = allItems.get(position);
            holder.text.setText(item.title);

            // set the icon of the image
            holder.text.setCompoundDrawablesWithIntrinsicBounds(item.icon, null, null, null);

            // update color
            ColorStateList color = (selected.orNull() == item) ? markedColor : defaultColor;
            holder.text.setTextColor(color);
            changeCompoundDrawableColor(holder.text, color.withAlpha(ICON_ALPHA));

            // handle clicks
            holder.itemView.setOnClickListener(v -> {
                if (item.hasFilter()) {
                    onFeedFilterClicked(item.filter);

                } else if (item.action != null) {
                    item.action.run();
                    onOtherNavigationItemClicked();
                }
            });

            holder.itemView.setOnLongClickListener(v -> {
                if (item.bookmark != null) {
                    showDialogToRemoveBookmark(item.bookmark);
                }

                return true;
            });

            item.bind.call(holder);
        }

        @Override
        public int getItemCount() {
            return allItems.size();
        }

        public void setNavigationItems(List<NavigationItem> items) {
            this.allItems.clear();
            this.allItems.addAll(items);
            merge();
        }

        public void setCurrentFilter(FeedFilter current) {
            currentFilter = current;
            merge();
        }

        private void merge() {
            // calculate the currently selected item
            selected = FluentIterable.from(allItems)
                    .filter(NavigationItem::hasFilter)
                    .filter(nav -> equal(currentFilter, nav.filter))
                    .first();

            if (!selected.isPresent() && currentFilter != null) {
                selected = FluentIterable.from(allItems)
                        .filter(NavigationItem::hasFilter)
                        .filter(nav -> nav.filter.getFeedType() == currentFilter.getFeedType())
                        .first();
            }

            notifyDataSetChanged();
        }
    }

    private void showDialogToRemoveBookmark(Bookmark bookmark) {
        DialogBuilder.start(getActivity())
                .content(R.string.do_you_want_to_remove_this_bookmark)
                .positive(R.string.yes, () -> bookmarkService.delete(bookmark))
                .negative(R.string.cancel)
                .show();
    }

    private class NavigationItemViewHolder extends RecyclerView.ViewHolder {
        final TextView text;

        public NavigationItemViewHolder(View itemView) {
            super(itemView);
            text = (TextView) (itemView instanceof TextView ?
                    itemView : itemView.findViewById(R.id.title));
        }
    }

    private static class NavigationItem {
        final String title;
        final FeedFilter filter;
        final Drawable icon;
        final Bookmark bookmark;

        Runnable action;
        Action1<NavigationItemViewHolder> bind = Actions.empty();
        int layout = R.layout.left_drawer_nav_item;

        NavigationItem(String title, Drawable icon) {
            this.title = title;
            this.icon = icon;
            this.filter = null;
            this.bookmark = null;
        }

        NavigationItem(FeedFilter filter, String title, Drawable icon) {
            this(filter, title, icon, null);
        }

        NavigationItem(FeedFilter filter, String title, Drawable icon, Bookmark bookmark) {
            this.title = title;
            this.filter = filter;
            this.icon = icon;
            this.bookmark = bookmark;
            this.action = null;
        }

        public NavigationItem withLayout(int layout) {
            this.layout = layout;
            return this;
        }

        public NavigationItem withExtraBind(Action1<NavigationItemViewHolder> bind) {
            this.bind = bind;
            return this;
        }

        public NavigationItem withClickAction(Runnable action) {
            this.action = action;
            return this;
        }

        public boolean hasFilter() {
            return filter != null;
        }
    }
}
