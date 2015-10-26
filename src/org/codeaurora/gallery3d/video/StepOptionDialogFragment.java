package org.codeaurora.gallery3d.video;

import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.content.DialogInterface;
import android.os.Bundle;

/** M: use DialogFragment to show Dialog */
public class StepOptionDialogFragment extends DialogFragment implements
        DialogInterface.OnClickListener{

    private static final String KEY_ITEM_ARRAY = "itemArray";
    private static final String KEY_TITLE = "title";
    private static final String KEY_DEFAULT_SELECT = "nowSelect";
    private DialogInterface.OnClickListener mClickListener = null;

    /**
     * M: create a instance of SelectDialogFragment
     * 
     * @param itemArrayID
     *            the resource id array of strings that show in list
     * @param sufffixArray
     *            the suffix array at the right of list item
     * @param titleID
     *            the resource id of title string
     * @param nowSelect
     *            the current select item index
     * @return the instance of SelectDialogFragment
     */
    public static StepOptionDialogFragment newInstance(int[] itemArrayID,
            int titleID, int nowSelect) {
        StepOptionDialogFragment frag = new StepOptionDialogFragment();
        Bundle args = new Bundle();
        args.putIntArray(KEY_ITEM_ARRAY, itemArrayID);
        args.putInt(KEY_TITLE, titleID);
        args.putInt(KEY_DEFAULT_SELECT, nowSelect);
        frag.setArguments(args);
        return frag;
    }

    @Override
    /**
     * M: create a select dialog
     */
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        Bundle args = getArguments();
        final String title = getString(args.getInt(KEY_TITLE));
        final int[] itemArrayID = args.getIntArray(KEY_ITEM_ARRAY);
        int arraySize = itemArrayID.length;
        CharSequence[] itemArray = new CharSequence[arraySize];
        for (int i = 0; i < arraySize; i++) {
            itemArray[i] = getString(itemArrayID[i]);
        }

        AlertDialog.Builder builder = null;
        int nowSelect = args.getInt(KEY_DEFAULT_SELECT);
        builder = new AlertDialog.Builder(getActivity());
        builder.setTitle(title).setSingleChoiceItems(itemArray, nowSelect, this)
                .setNegativeButton(getString(android.R.string.cancel), null);
        return builder.create();
    }

    @Override
    /**
     * M: the process of select an item
     */
    public void onClick(DialogInterface arg0, int arg1) {
        if (null != mClickListener) {
            mClickListener.onClick(arg0, arg1);
        }
    }

    /**
     * M: set listener of click items
     * 
     * @param listener
     *            the listener to be set
     */
    public void setOnClickListener(DialogInterface.OnClickListener listener) {
        mClickListener = listener;
    }
}