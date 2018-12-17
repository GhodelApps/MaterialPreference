package com.anggrayudi.materialpreference;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.media.AudioManager;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.provider.MediaStore;
import android.util.Log;
import android.view.View;

import com.afollestad.materialdialogs.DialogAction;
import com.afollestad.materialdialogs.MaterialDialog;
import com.anggrayudi.materialpreference.dialog.PreferenceDialogFragment;

import java.util.ArrayList;
import java.util.List;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Created by Eugen on 07.12.2015.
 */
public class RingtonePreferenceDialogFragment extends PreferenceDialogFragment
        implements Runnable {

    private static final String TAG = "RingtonePreference";

    private static int RC_FALLBACK_RINGTONE_PICKER = 0xff00; // <0; 0xffff>

    private static String KEY_FALLBACK_RINGTONE_PICKER = "com.anggrayudi.materialpreference.FALLBACK_RINGTONE_PICKER";

    private static final int POS_UNKNOWN = -1;

    private static final int DELAY_MS_SELECTION_PLAYED = 300;

    private static final String SAVE_CLICKED_POS = "clicked_pos";

    private RingtoneManager mRingtoneManager;
    private int mType;

    private Cursor mCursor;
    private Handler mHandler;

    private int mUnknownPos = POS_UNKNOWN;

    /**
     * The position in the list of the 'Silent' item.
     */
    private int mSilentPos = POS_UNKNOWN;

    /**
     * The position in the list of the 'Default' item.
     */
    private int mDefaultRingtonePos = POS_UNKNOWN;

    /**
     * The position in the list of the last clicked item.
     */
    int mClickedPos = POS_UNKNOWN;

    /**
     * The position in the list of the ringtone to sample.
     */
    private int mSampleRingtonePos = POS_UNKNOWN;

    /**
     * Whether this list has the 'Silent' item.
     */
    private boolean mHasSilentItem;

    /**
     * The Uri to place a checkmark next to.
     */
    private Uri mExistingUri;

    /**
     * The number of static items in the list.
     */
    private final ArrayList<CharSequence> mStaticItems = new ArrayList<>();

    /**
     * Whether this list has the 'Default' item.
     */
    private boolean mHasDefaultItem;

    /**
     * The Uri to play when the 'Default' item is clicked.
     */
    private Uri mUriForDefaultItem;

    private Ringtone mUnknownRingtone;

    /**
     * A Ringtone for the default ringtone. In most cases, the RingtoneManager
     * will stop the previous ringtone. However, the RingtoneManager doesn't
     * manage the default ringtone for us, so we should stop this one manually.
     */
    private Ringtone mDefaultRingtone;

    /**
     * The ringtone that's currently playing, unless the currently playing one is the default
     * ringtone.
     */
    private Ringtone mCurrentRingtone;

    /**
     * Keep the currently playing ringtone around when changing orientation, so that it
     * can be stopped later, after the activity is recreated.
     */
    private static Ringtone sPlayingRingtone;

    public static RingtonePreferenceDialogFragment newInstance(String key) {
        RingtonePreferenceDialogFragment fragment = new RingtonePreferenceDialogFragment();
        Bundle b = new Bundle(1);
        b.putString(ARG_KEY, key);
        fragment.setArguments(b);
        return fragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mHandler = new Handler();
        loadRingtoneManager(savedInstanceState);
    }

    private void loadRingtoneManager(@Nullable Bundle savedInstanceState) {
        // Give the Activity so it can do managed queries
        mRingtoneManager = new RingtoneManagerCompat(getActivity());

        final boolean fallbackRingtonePicker;
        if (savedInstanceState != null) {
            mClickedPos = savedInstanceState.getInt(SAVE_CLICKED_POS, POS_UNKNOWN);
            fallbackRingtonePicker = savedInstanceState.getBoolean(KEY_FALLBACK_RINGTONE_PICKER);
        } else {
            fallbackRingtonePicker = false;
        }

        if (fallbackRingtonePicker) {
            setShowsDialog(false);
        } else {
            RingtonePreference preference = requireRingtonePreference();

            /*
             * Get whether to show the 'Default' item, and the URI to play when the
             * default is clicked
             */
            mHasDefaultItem = preference.getShowDefault();
            mUriForDefaultItem = RingtoneManager.getDefaultUri(preference.getRingtoneType());

            // Get whether to show the 'Silent' item
            mHasSilentItem = preference.getShowSilent();

            // Get the types of ringtones to show
            mType = preference.getRingtoneType();
            if (mType != -1) {
                mRingtoneManager.setType(mType);
            }

            // Get the URI whose list item should have a checkmark
            mExistingUri = preference.onRestoreRingtone();

            try {
                mCursor = mRingtoneManager.getCursor();

                // Check if cursor is valid.
                mCursor.getColumnNames();
            } catch (IllegalStateException ex) {
                recover(preference, ex);
            } catch (IllegalArgumentException ex) {
                recover(preference, ex);
            }
        }
    }

    private void recover(@NonNull final RingtonePreference preference, @NonNull final Throwable ex) {
        Log.e(TAG, "RingtoneManager returned unexpected cursor.", ex);

        mCursor = null;
        setShowsDialog(false);

        // Alternatively try starting system picker.
        Intent i = preference.buildRingtonePickerIntent();
        try {
            startActivityForResult(i, RC_FALLBACK_RINGTONE_PICKER);
        } catch (ActivityNotFoundException ex2) {
            onRingtonePickerNotFound(RC_FALLBACK_RINGTONE_PICKER);
        }
    }

    /**
     * Called when there's no ringtone picker available in the system.
     * Let the user know (using e.g. a Toast).
     * Just dismisses this fragment by default.
     *
     * @param requestCode You can use this code to launch another activity instead of dismissing
     *                    this fragment. The result must contain
     *                    {@link RingtoneManager#EXTRA_RINGTONE_PICKED_URI} extra.
     */
    public void onRingtonePickerNotFound(final int requestCode) {
        dismiss();
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == RC_FALLBACK_RINGTONE_PICKER) {
            if (resultCode == Activity.RESULT_OK) {
                requireRingtonePreference().onActivityResult(data);
            }
            dismiss();
        }
    }

    @Override
    protected void onPrepareDialogBuilder(@NonNull MaterialDialog.Builder builder) {
        super.onPrepareDialogBuilder(builder);

        RingtonePreference preference = requireRingtonePreference();

        // The volume keys will control the stream that we are choosing a ringtone for
        getActivity().setVolumeControlStream(mRingtoneManager.inferStreamType());

        CharSequence title = preference.getNonEmptyDialogTitle();
        builder.title(title);

        final Context context = builder.getContext();

        if (mHasDefaultItem) {
            mDefaultRingtonePos = addDefaultRingtoneItem();

            if (mClickedPos == POS_UNKNOWN && RingtoneManager.isDefault(mExistingUri)) {
                mClickedPos = mDefaultRingtonePos;
            }
        }
        if (mHasSilentItem) {
            mSilentPos = addSilentItem();

            // The 'Silent' item should use a null Uri
            if (mClickedPos == POS_UNKNOWN && mExistingUri == null) {
                mClickedPos = mSilentPos;
            }
        }

        if (mClickedPos == POS_UNKNOWN) {
            mClickedPos = getListPosition(mRingtoneManager.getRingtonePosition(mExistingUri));
        }

        // If we still don't have selected item, but we're not silent, show the 'Unknown' item.
        if (mClickedPos == POS_UNKNOWN && mExistingUri != null) {
            final String ringtoneTitle;
            final SafeRingtone ringtone = SafeRingtone.obtain(context, mExistingUri);
            try {
                // We may not be able to list external ringtones
                // but we may be able to show selected external ringtone title.
                if (ringtone.canGetTitle()) {
                    ringtoneTitle = ringtone.getTitle();
                } else {
                    ringtoneTitle = null;
                }
            } finally {
                ringtone.stop();
            }
            if (ringtoneTitle == null) {
                mUnknownPos = addUnknownItem();
            } else {
                mUnknownPos = addStaticItem(ringtoneTitle);
            }
            mClickedPos = mUnknownPos;
        }

        List<CharSequence> titles = new ArrayList<>(mStaticItems);
        if (mCursor.moveToFirst()) {
            int index = mCursor.getColumnIndex(MediaStore.Audio.Media.TITLE);
            do {
                titles.add(mCursor.getString(index));
            } while (mCursor.moveToNext());
        }

        builder.autoDismiss(false)
                .items(titles)
                .alwaysCallSingleChoiceCallback()
                .itemsCallbackSingleChoice(mClickedPos, new MaterialDialog.ListCallbackSingleChoice() {
                    @Override
                    public boolean onSelection(MaterialDialog dialog, View itemView, int which, CharSequence text) {
                        mClickedPos = which;
                        playRingtone(which, DELAY_MS_SELECTION_PLAYED);
                        return true;
                    }
                })
                .onNegative(new MaterialDialog.SingleButtonCallback() {
                    @Override
                    public void onClick(@NonNull MaterialDialog dialog, @NonNull DialogAction which) {
                        dialog.dismiss();
                    }
                })
                .onPositive(new MaterialDialog.SingleButtonCallback() {
                    @Override
                    public void onClick(@NonNull MaterialDialog dialog, @NonNull DialogAction which) {
                        RingtonePreferenceDialogFragment.this.onClick(dialog, DialogAction.POSITIVE);
                        dialog.dismiss();
                    }
                });
    }

    /**
     * Adds a static item to the top of the list. A static item is one that is not from the
     * RingtoneManager.
     *
     * @param text Text for the item.
     * @return The position of the inserted item.
     */
    private int addStaticItem(@NonNull CharSequence text) {
        mStaticItems.add(text);
        return mStaticItems.size() - 1;
    }

    private int addDefaultRingtoneItem() {
        switch (mType) {
            case RingtoneManager.TYPE_NOTIFICATION:
                return addStaticItem(RingtonePreference.getNotificationSoundDefaultString(getContext()));
            case RingtoneManager.TYPE_ALARM:
                return addStaticItem(RingtonePreference.getAlarmSoundDefaultString(getContext()));
            default:
                return addStaticItem(RingtonePreference.getRingtoneDefaultString(getContext()));
        }
    }

    private int addSilentItem() {
        return addStaticItem(RingtonePreference.getRingtoneSilentString(getContext()));
    }

    private int addUnknownItem() {
        return addStaticItem(RingtonePreference.getRingtoneUnknownString(getContext()));
    }

    private int getListPosition(int ringtoneManagerPos) {
        // If the manager position is -1 (for not found), return that
        if (ringtoneManagerPos < 0) return POS_UNKNOWN;

        return ringtoneManagerPos + mStaticItems.size();
    }

    @Override
    public void onPause() {
        super.onPause();
        if (!getActivity().isChangingConfigurations()) {
            stopAnyPlayingRingtone();
        }
    }

    @Override
    public void onStop() {
        super.onStop();
        if (!getActivity().isChangingConfigurations()) {
            stopAnyPlayingRingtone();
        } else {
            saveAnyPlayingRingtone();
        }
    }

    @Override
    public void onSaveInstanceState(@NonNull final Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt(SAVE_CLICKED_POS, mClickedPos);
        outState.putBoolean(KEY_FALLBACK_RINGTONE_PICKER, !getShowsDialog());
    }

    public RingtonePreference getRingtonePreference() {
        return (RingtonePreference) getPreference();
    }

    @NonNull
    protected RingtonePreference requireRingtonePreference() {
        final RingtonePreference preference = getRingtonePreference();
        if (preference == null) {
            final String key = getArguments().getString(ARG_KEY);
            throw new IllegalStateException("RingtonePreference[" + key + "] not available (yet).");
        }
        return preference;
    }

    @Override
    public void onDialogClosed(boolean positiveResult) {
        // Stop playing the previous ringtone
        if (sPlayingRingtone == null) {
            mRingtoneManager.stopPreviousRingtone();
        }

        // The volume keys will control the default stream
        if (getActivity() != null) {
            getActivity().setVolumeControlStream(AudioManager.USE_DEFAULT_STREAM_TYPE);
        }

        if (positiveResult) {
            Uri uri;
            if (mClickedPos == mDefaultRingtonePos) {
                // Set it to the default Uri that they originally gave us
                uri = mUriForDefaultItem;
            } else if (mClickedPos == mSilentPos) {
                // A null Uri is for the 'Silent' item
                uri = null;
            } else if (mClickedPos == mUnknownPos) {
                // 'Unknown' was shown because it was persisted before showing the picker.
                // There's no change to persist, return immediately.
                return;
            } else {
                uri = mRingtoneManager.getRingtoneUri(getRingtoneManagerPosition(mClickedPos));
            }

            requireRingtonePreference().saveRingtone(uri);
        }
    }

    void playRingtone(int position, int delayMs) {
        mHandler.removeCallbacks(this);
        mSampleRingtonePos = position;
        mHandler.postDelayed(this, delayMs);
    }

    public void run() {
        stopAnyPlayingRingtone();
        if (mSampleRingtonePos == mSilentPos) {
            return;
        }

//        final int oldSampleRingtonePos = mSampleRingtonePos;
        try {
            Ringtone ringtone = null;
            if (mSampleRingtonePos == mDefaultRingtonePos) {
                if (mDefaultRingtone == null) {
                    try {
                        mDefaultRingtone = RingtoneManager.getRingtone(getContext(), mUriForDefaultItem);
                    } catch (SecurityException ex) {
                        Log.e(TAG, "Failed to create default Ringtone from " + mUriForDefaultItem + ".", ex);
                    }
                }
                /*
                 * Stream type of mDefaultRingtone is not set explicitly here.
                 * It should be set in accordance with mRingtoneManager of this Activity.
                 */
                if (mDefaultRingtone != null) {
                    mDefaultRingtone.setStreamType(mRingtoneManager.inferStreamType());
                }
                ringtone = mDefaultRingtone;
                mCurrentRingtone = null;
            } else if (mSampleRingtonePos == mUnknownPos) {
                if (mUnknownRingtone == null) {
                    try {
                        mUnknownRingtone = RingtoneManager.getRingtone(getContext(), mExistingUri);
                    } catch (SecurityException ex) {
                        Log.e(TAG, "Failed to create unknown Ringtone from " + mExistingUri + ".", ex);
                    }
                }
                if (mUnknownRingtone != null) {
                    mUnknownRingtone.setStreamType(mRingtoneManager.inferStreamType());
                }
                ringtone = mUnknownRingtone;
                mCurrentRingtone = null;
            } else {
                final int position = getRingtoneManagerPosition(mSampleRingtonePos);
                try {
                    ringtone = mRingtoneManager.getRingtone(position);
                } catch (SecurityException ex) {
                    Log.e(TAG, "Failed to create selected Ringtone from " + mRingtoneManager.getRingtoneUri(position) + ".", ex);
                }
                mCurrentRingtone = ringtone;
            }

            if (ringtone != null) {
                ringtone.play();
            }
        } catch (SecurityException ex) {
            // Don't play the inaccessible default ringtone.
            Log.e(TAG, "Failed to play Ringtone.", ex);
//            mSampleRingtonePos = oldSampleRingtonePos;
        }
    }

    private void saveAnyPlayingRingtone() {
        if (mDefaultRingtone != null && mDefaultRingtone.isPlaying()) {
            sPlayingRingtone = mDefaultRingtone;
        } else if (mUnknownRingtone != null && mUnknownRingtone.isPlaying()) {
            sPlayingRingtone = mUnknownRingtone;
        } else if (mCurrentRingtone != null && mCurrentRingtone.isPlaying()) {
            sPlayingRingtone = mCurrentRingtone;
        }
    }

    private void stopAnyPlayingRingtone() {
        if (sPlayingRingtone != null && sPlayingRingtone.isPlaying()) {
            sPlayingRingtone.stop();
        }
        sPlayingRingtone = null;

        if (mDefaultRingtone != null && mDefaultRingtone.isPlaying()) {
            mDefaultRingtone.stop();
        }

        if (mUnknownRingtone != null && mUnknownRingtone.isPlaying()) {
            mUnknownRingtone.stop();
        }

        if (mRingtoneManager != null) {
            mRingtoneManager.stopPreviousRingtone();
        }
    }

    private int getRingtoneManagerPosition(int listPos) {
        return listPos - mStaticItems.size();
    }
}
