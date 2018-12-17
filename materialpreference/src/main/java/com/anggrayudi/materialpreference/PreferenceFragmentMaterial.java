package com.anggrayudi.materialpreference;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.res.TypedArray;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.TypedValue;
import android.view.ContextThemeWrapper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.Toast;

import com.anggrayudi.materialpreference.dialog.DialogPreference;
import com.anggrayudi.materialpreference.util.FileUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RestrictTo;
import androidx.annotation.XmlRes;
import androidx.core.widget.NestedScrollView;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.RecyclerView;

import static androidx.annotation.RestrictTo.Scope.LIBRARY_GROUP;

/**
 * @author Anggrayudi on July 1st, 2018.
 */
@SuppressLint({"RestrictedApi", "PrivateResource"})
public abstract class PreferenceFragmentMaterial extends Fragment implements
        PreferenceManager.OnPreferenceTreeClickListener,
        PreferenceManager.OnDisplayPreferenceDialogListener,
        PreferenceManager.OnNavigateToScreenListener,
        DialogPreference.TargetFragment {

    private static final String TAG = "PreferenceFragment";

    /**
     * Fragment argument used to specify the tag of the desired root
     * {@link PreferenceScreen} object.
     */
    public static final String ARG_PREFERENCE_ROOT = "com.anggrayudi.materialpreference.PreferenceFragmentMaterial.PREFERENCE_ROOT";
    private static final String PREFERENCES_TAG = "android:preferences";
    static final String DIALOG_FRAGMENT_TAG = "com.anggrayudi.materialpreference.PreferenceFragment.DIALOG";
    static final String PREFERENCE_TITLE = "com.anggrayudi.materialpreference.PreferenceFragment.TITLE";

    private PreferenceManager mPreferenceManager;
    private NestedScrollView mScrollView;
    private LinearLayout mListContainer;
    //    private RecyclerView mList;
    private boolean mHavePrefs;
    private boolean mInitDone;
    String preferenceKeyOnActivityResult;

    private Context mStyledContext;

    private static final int MSG_BIND_PREFERENCES = 1;
    private Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {

                case MSG_BIND_PREFERENCES:
                    bindPreferences();
                    break;
            }
        }
    };

    final private Runnable mRequestFocus = new Runnable() {
        @Override
        public void run() {
            mScrollView.focusableViewAvailable(mScrollView);
        }
    };

    private Runnable mSelectPreferenceRunnable;

    /**
     * Interface that PreferenceFragment's containing activity should
     * implement to be able to process preference items that wish to
     * switch to a specified fragment.
     */
    public interface OnPreferenceStartFragmentCallback {
        /**
         * Called when the user has clicked on a Preference that has
         * a fragment class name associated with it.  The implementation
         * should instantiate and switch to an instance of the given
         * fragment.
         *
         * @param caller The fragment requesting navigation.
         * @param pref   The preference requesting the fragment.
         * @return true if the fragment creation has been handled
         */
        boolean onPreferenceStartFragment(PreferenceFragmentMaterial caller, Preference pref);
    }

    /**
     * Interface that PreferenceFragment's containing activity should
     * implement to be able to process preference items that wish to
     * switch to a new screen of preferences.
     */
    public interface OnPreferenceStartScreenCallback {
        /**
         * Called when the user has clicked on a PreferenceScreen item in order to navigate to a new
         * screen of preferences.
         *
         * @param caller The fragment requesting navigation.
         * @param pref   The preference screen to navigate to.
         * @return true if the screen navigation has been handled
         */
        boolean onPreferenceStartScreen(PreferenceFragmentMaterial caller, PreferenceScreen pref);
    }

    public interface OnPreferenceDisplayDialogCallback {

        /**
         * @param caller The fragment containing the preference requesting the dialog.
         * @param pref   The preference requesting the dialog.
         * @return true if the dialog creation has been handled.
         */
        boolean onPreferenceDisplayDialog(@NonNull PreferenceFragmentMaterial caller, Preference pref);
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        final TypedValue tv = new TypedValue();
        getActivity().getTheme().resolveAttribute(R.attr.preferenceTheme, tv, true);
        final int theme = tv.resourceId;
        if (theme == 0) {
            throw new IllegalStateException("Must specify preferenceTheme in theme");
        }
        if (getArguments() == null) {
            throw new IllegalStateException("Must specify non-null PreferenceFragmentMaterial arguments");
        }
        mStyledContext = new ContextThemeWrapper(getActivity(), theme);
        mPreferenceManager = new PreferenceManager(mStyledContext);
        mPreferenceManager.setOnNavigateToScreenListener(this);
        String rootKey = getArguments().getString(ARG_PREFERENCE_ROOT);
        if (rootKey == null && savedInstanceState == null)
            getArguments().putCharSequence(PREFERENCE_TITLE,
                    ((PreferenceActivityMaterial) getActivity()).getActivityLabel());

        onCreatePreferences(savedInstanceState, rootKey);
        ((PreferenceActivityMaterial) getActivity()).onCreatePreferences(this, rootKey);
    }

    public String getPreferenceFragmentTitle() {
        return getArguments().getString(PREFERENCE_TITLE);
    }

    /**
     * Called during {@link #onCreate(Bundle)} to supply the preferences for this fragment.
     * Subclasses are expected to call {@link #setPreferenceScreen(PreferenceScreen)} either
     * directly or via helper methods such as {@link #addPreferencesFromResource(int)}.
     *
     * @param savedInstanceState If the fragment is being re-created from
     *                           a previous saved state, this is the state.
     * @param rootKey            If non-null, this preference fragment should be rooted at the
     *                           {@link PreferenceScreen} with this key.
     */
    public abstract void onCreatePreferences(Bundle savedInstanceState, String rootKey);

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        TypedArray a = mStyledContext.obtainStyledAttributes(null,
                R.styleable.PreferenceFragmentMaterial,
                R.attr.preferenceFragmentCompatStyle,
                0);

        a.recycle();

        // Need to theme the inflater to pick up the preferenceFragmentListStyle
        final TypedValue tv = new TypedValue();
        getActivity().getTheme().resolveAttribute(R.attr.preferenceTheme, tv, true);
        final int theme = tv.resourceId;

        final Context themedContext = new ContextThemeWrapper(inflater.getContext(), theme);
        final LayoutInflater themedInflater = inflater.cloneInContext(themedContext);
        mScrollView = (NestedScrollView) themedInflater.inflate(R.layout.preference_scrollview, container, false);
        mListContainer = mScrollView.findViewById(R.id.list_container);
        mHandler.post(mRequestFocus);
        return mScrollView;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        if (mHavePrefs) {
            bindPreferences();
            if (mSelectPreferenceRunnable != null) {
                mSelectPreferenceRunnable.run();
                mSelectPreferenceRunnable = null;
            }
        }

        mInitDone = true;
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        if (savedInstanceState != null) {
            preferenceKeyOnActivityResult = savedInstanceState.getString("preferenceKeyOnActivityResult");
            Bundle container = savedInstanceState.getBundle(PREFERENCES_TAG);
            if (container != null) {
                final PreferenceScreen preferenceScreen = getPreferenceScreen();
                if (preferenceScreen != null) {
                    preferenceScreen.restoreHierarchyState(container);
                }
            }
        }
    }

    @Override
    public void onStart() {
        super.onStart();
        mPreferenceManager.setOnPreferenceTreeClickListener(this);
        mPreferenceManager.setOnDisplayPreferenceDialogListener(this);
    }

    @Override
    public void onStop() {
        super.onStop();
        mPreferenceManager.setOnPreferenceTreeClickListener(null);
        mPreferenceManager.setOnDisplayPreferenceDialogListener(null);
    }

    @Override
    public void onDestroyView() {
        mHandler.removeCallbacks(mRequestFocus);
        mHandler.removeMessages(MSG_BIND_PREFERENCES);
        if (mHavePrefs) {
            unbindPreferences();
        }
        super.onDestroyView();
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString("preferenceKeyOnActivityResult", preferenceKeyOnActivityResult);
        final PreferenceScreen preferenceScreen = getPreferenceScreen();
        if (preferenceScreen != null) {
            Bundle container = new Bundle();
            preferenceScreen.saveHierarchyState(container);
            outState.putBundle(PREFERENCES_TAG, container);
        }
    }

    /**
     * Returns the {@link PreferenceManager} used by this fragment.
     *
     * @return The {@link PreferenceManager}.
     */
    public PreferenceManager getPreferenceManager() {
        return mPreferenceManager;
    }

    /**
     * Sets the root of the preference hierarchy that this fragment is showing.
     *
     * @param preferenceScreen The root {@link PreferenceScreen} of the preference hierarchy.
     */
    public void setPreferenceScreen(PreferenceScreen preferenceScreen) {
        if (mPreferenceManager.setPreferences(preferenceScreen) && preferenceScreen != null) {
            onUnbindPreferences();
            mHavePrefs = true;
            if (mInitDone) {
                postBindPreferences();
            }
        }
    }

    /**
     * Gets the root of the preference hierarchy that this fragment is showing.
     *
     * @return The {@link PreferenceScreen} that is the root of the preference
     * hierarchy.
     */
    public PreferenceScreen getPreferenceScreen() {
        return mPreferenceManager.getPreferenceScreen();
    }

    /**
     * Inflates the given XML resource and adds the preference hierarchy to the current
     * preference hierarchy.
     *
     * @param preferencesResId The XML resource ID to inflate.
     */
    public void addPreferencesFromResource(@XmlRes int preferencesResId) {
        requirePreferenceManager();

        setPreferenceScreen(mPreferenceManager.inflateFromResource(mStyledContext,
                preferencesResId, getPreferenceScreen()));
    }

    /**
     * Inflates the given XML resource and replaces the current preference hierarchy (if any) with
     * the preference hierarchy rooted at {@code key}.
     *
     * @param preferencesResId The XML resource ID to inflate.
     * @param key              The preference key of the {@link PreferenceScreen}
     *                         to use as the root of the preference hierarchy, or null to use the root
     *                         {@link PreferenceScreen}.
     */
    public void setPreferencesFromResource(@XmlRes int preferencesResId, @Nullable String key) {
        requirePreferenceManager();

        final PreferenceScreen xmlRoot = mPreferenceManager.inflateFromResource(mStyledContext,
                preferencesResId, null);

        final Preference root;
        if (key != null) {
            root = xmlRoot.findPreference(key);
            if (!(root instanceof PreferenceScreen)) {
                throw new IllegalArgumentException("Preference object with key " + key
                        + " is not a PreferenceScreen");
            }
        } else {
            root = xmlRoot;
        }

        setPreferenceScreen((PreferenceScreen) root);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean onPreferenceTreeClick(Preference preference) {
        if (preference.getFragment() != null) {
            boolean handled = false;
            if (getCallbackFragment() instanceof PreferenceFragmentMaterial.OnPreferenceStartFragmentCallback) {
                handled = ((PreferenceFragmentMaterial.OnPreferenceStartFragmentCallback) getCallbackFragment())
                        .onPreferenceStartFragment(this, preference);
            }
            if (!handled && getActivity() instanceof PreferenceFragmentMaterial.OnPreferenceStartFragmentCallback) {
                handled = ((PreferenceFragmentMaterial.OnPreferenceStartFragmentCallback) getActivity())
                        .onPreferenceStartFragment(this, preference);
            }
            return handled;
        }
        return false;
    }

    /**
     * Called by
     * {@link PreferenceScreen#onClick()} in order to navigate to a
     * new screen of preferences. Calls
     * {@link PreferenceFragmentMaterial.OnPreferenceStartScreenCallback#onPreferenceStartScreen}
     * if the target fragment or containing activity implements
     * {@link PreferenceFragmentMaterial.OnPreferenceStartScreenCallback}.
     *
     * @param preferenceScreen The {@link PreferenceScreen} to
     *                         navigate to.
     */
    @Override
    public void onNavigateToScreen(PreferenceScreen preferenceScreen) {
        boolean handled = false;
        if (getCallbackFragment() instanceof PreferenceFragmentMaterial.OnPreferenceStartScreenCallback) {
            handled = ((PreferenceFragmentMaterial.OnPreferenceStartScreenCallback) getCallbackFragment())
                    .onPreferenceStartScreen(this, preferenceScreen);
        }
        if (!handled && getActivity() instanceof PreferenceFragmentMaterial.OnPreferenceStartScreenCallback) {
            ((PreferenceFragmentMaterial.OnPreferenceStartScreenCallback) getActivity())
                    .onPreferenceStartScreen(this, preferenceScreen);
        }
    }

    /**
     * Finds a {@link Preference} based on its key.
     *
     * @param key The key of the preference to retrieve.
     * @return The {@link Preference} with the key, or null.
     * @see PreferenceGroup#findPreference(CharSequence)
     */
    @Override
    public Preference findPreference(CharSequence key) {
        if (mPreferenceManager == null) {
            return null;
        }
        return mPreferenceManager.findPreference(key);
    }

    private void requirePreferenceManager() {
        if (mPreferenceManager == null) {
            throw new RuntimeException("This should be called after super.onCreate.");
        }
    }

    private void postBindPreferences() {
        if (mHandler.hasMessages(MSG_BIND_PREFERENCES)) return;
        mHandler.obtainMessage(MSG_BIND_PREFERENCES).sendToTarget();
    }

    private void bindPreferences() {
        final PreferenceScreen preferenceScreen = getPreferenceScreen();
        if (preferenceScreen != null) {
            attachPreferences(preferenceScreen);
            preferenceScreen.onAttached();
        }
        onBindPreferences();
    }

    private void unbindPreferences() {
        final PreferenceScreen preferenceScreen = getPreferenceScreen();
        if (preferenceScreen != null) {
            preferenceScreen.onDetached();
        }
        onUnbindPreferences();
    }

    /**
     * @hide
     */
    @RestrictTo(LIBRARY_GROUP)
    protected void onBindPreferences() {
    }

    /**
     * @hide
     */
    @RestrictTo(LIBRARY_GROUP)
    protected void onUnbindPreferences() {
    }

    private PreferenceGroupAdapter mAdapter;

    private void attachPreferences(PreferenceScreen screen) {
        // TODO: 01/07/18 Attach to cardviews
        mAdapter = new PreferenceGroupAdapter(this, screen, mListContainer);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != Activity.RESULT_OK) {
            preferenceKeyOnActivityResult = null;
            return;
        }

        if (requestCode == FileUtils.REQUEST_CODE_STORAGE_GET_FOLDER) {
            String path = FileUtils.resolvePathFromUri(data);
            if (path.startsWith("/") || FileUtils.isSdCardUriPermissionsGranted(getContext(), data)) {
                if (preferenceKeyOnActivityResult != null) {
                    FolderPreference preference = (FolderPreference) findPreference(preferenceKeyOnActivityResult);
                    if (preference.callChangeListener(path)) {
                        preference.persistString(path);
                        preference.setSummary(path);
                    }
                }
                preferenceKeyOnActivityResult = null;
            } else {
                Toast.makeText(getContext(), R.string.please_select_sdcard_root, Toast.LENGTH_LONG).show();
                startActivityForResult(new Intent("android.intent.action.OPEN_DOCUMENT_TREE"),
                        FileUtils.REQUEST_CODE_REQUIRE_SDCARD_ROOT_PATH_PERMISSIONS);
            }
        } else if (requestCode == FileUtils.REQUEST_CODE_REQUIRE_SDCARD_ROOT_PATH_PERMISSIONS && FileUtils.saveUriPermission(getContext(), data)) {
            Toast.makeText(getContext(), R.string.you_can_select_sdcard, Toast.LENGTH_SHORT).show();
            startActivityForResult(new Intent("android.intent.action.OPEN_DOCUMENT_TREE"),
                    FileUtils.REQUEST_CODE_STORAGE_GET_FOLDER);
        } else {
            preferenceKeyOnActivityResult = null;
        }
    }

    /**
     * Called when a preference in the tree requests to display a dialog. Subclasses should
     * override this method to display custom dialogs or to handle dialogs for custom preference
     * classes.
     *
     * @param preference The Preference object requesting the dialog.
     */
    @Override
    public void onDisplayPreferenceDialog(Preference preference) {

        boolean handled = false;
        if (getCallbackFragment() instanceof PreferenceFragmentMaterial.OnPreferenceDisplayDialogCallback) {
            handled = ((PreferenceFragmentMaterial.OnPreferenceDisplayDialogCallback) getCallbackFragment())
                    .onPreferenceDisplayDialog(this, preference);
        }
        if (!handled && getActivity() instanceof PreferenceFragmentMaterial.OnPreferenceDisplayDialogCallback) {
            handled = ((PreferenceFragmentMaterial.OnPreferenceDisplayDialogCallback) getActivity())
                    .onPreferenceDisplayDialog(this, preference);
        }

        if (handled) {
            return;
        }

        // check if dialog is already showing
        if (getFragmentManager().findFragmentByTag(DIALOG_FRAGMENT_TAG) != null) {
            return;
        }

        final DialogFragment f;
        if (preference instanceof EditTextPreference) {
            f = EditTextPreferenceDialogFragment.newInstance(preference.getKey());
        } else if (preference instanceof ListPreference) {
            f = ListPreferenceDialogFragment.newInstance(preference.getKey());
        } else if (preference instanceof MultiSelectListPreference) {
            f = MultiSelectListPreferenceDialogFragment.newInstance(preference.getKey());
        } else if (preference instanceof RingtonePreference) {
            f = RingtonePreferenceDialogFragment.newInstance(preference.getKey());
        } else if (preference instanceof SeekBarDialogPreference) {
            f = SeekBarPreferenceDialogFragment.newInstance(preference.getKey());
        } else {
            throw new IllegalArgumentException("Tried to display dialog for unknown " +
                    "preference type. Did you forget to override onDisplayPreferenceDialog()?");
        }
        f.setTargetFragment(this, 0);
        f.show(getFragmentManager(), DIALOG_FRAGMENT_TAG);
    }

    /**
     * Basically a wrapper for getParentFragment which is v17+. Used by the leanback preference lib.
     *
     * @return Fragment to possibly use as a callback
     * @hide
     */
    @RestrictTo(LIBRARY_GROUP)
    public Fragment getCallbackFragment() {
        return this;
    }

    public void scrollToPreference(final String key) {
        scrollToPreferenceInternal(null, key);
    }

    public void scrollToPreference(final Preference preference) {
        scrollToPreferenceInternal(preference, null);
    }

    private void scrollToPreferenceInternal(final Preference preference, final String key) {
        // TODO: 01/07/18 Lakukan dengan NestedScrollView
//        final Runnable r = new Runnable() {
//            @Override
//            public void run() {
//                final RecyclerView.Adapter adapter = mList.getAdapter();
//                if (!(adapter instanceof
//                        PreferenceGroup.PreferencePositionCallback)) {
//                    if (adapter != null) {
//                        throw new IllegalStateException("Adapter must implement "
//                                + "PreferencePositionCallback");
//                    } else {
//                        // Adapter was set to null, so don't scroll I guess?
//                        return;
//                    }
//                }
//                final int position;
//                if (preference != null) {
//                    position = ((PreferenceGroup.PreferencePositionCallback) adapter)
//                            .getPreferenceAdapterPosition(preference);
//                } else {
//                    position = ((PreferenceGroup.PreferencePositionCallback) adapter)
//                            .getPreferenceAdapterPosition(key);
//                }
//                if (position != RecyclerView.NO_POSITION) {
//                    mList.scrollToPosition(position);
//                } else {
//                    // Item not found, wait for an update and try again
//                    adapter.registerAdapterDataObserver(
//                            new PreferenceFragmentMaterial.ScrollToPreferenceObserver(adapter, mList, preference, key));
//                }
//            }
//        };
//        if (mList == null) {
//            mSelectPreferenceRunnable = r;
//        } else {
//            r.run();
//        }
    }

    private static class ScrollToPreferenceObserver extends RecyclerView.AdapterDataObserver {
        private final RecyclerView.Adapter mAdapter;
        private final RecyclerView mList;
        private final Preference mPreference;
        private final String mKey;

        public ScrollToPreferenceObserver(RecyclerView.Adapter adapter, RecyclerView list,
                                          Preference preference, String key) {
            mAdapter = adapter;
            mList = list;
            mPreference = preference;
            mKey = key;
        }

        private void scrollToPreference() {
            mAdapter.unregisterAdapterDataObserver(this);
            final int position;
            if (mPreference != null) {
                position = ((PreferenceGroup.PreferencePositionCallback) mAdapter)
                        .getPreferenceAdapterPosition(mPreference);
            } else {
                position = ((PreferenceGroup.PreferencePositionCallback) mAdapter)
                        .getPreferenceAdapterPosition(mKey);
            }
            if (position != RecyclerView.NO_POSITION) {
                mList.scrollToPosition(position);
            }
        }

        @Override
        public void onChanged() {
            scrollToPreference();
        }

        @Override
        public void onItemRangeChanged(int positionStart, int itemCount) {
            scrollToPreference();
        }

        @Override
        public void onItemRangeChanged(int positionStart, int itemCount, Object payload) {
            scrollToPreference();
        }

        @Override
        public void onItemRangeInserted(int positionStart, int itemCount) {
            scrollToPreference();
        }

        @Override
        public void onItemRangeRemoved(int positionStart, int itemCount) {
            scrollToPreference();
        }

        @Override
        public void onItemRangeMoved(int fromPosition, int toPosition, int itemCount) {
            scrollToPreference();
        }
    }
}
