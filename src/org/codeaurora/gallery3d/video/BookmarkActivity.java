package org.codeaurora.gallery3d.video;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.AdapterView.AdapterContextMenuInfo;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.SimpleCursorAdapter;
import android.widget.TextView;

import com.android.gallery3d.R;
import com.android.gallery3d.app.MovieActivity;

public class BookmarkActivity extends Activity implements OnItemClickListener {
    private static final String TAG = "BookmarkActivity";
    private static final boolean LOG = false;

    private BookmarkEnhance mBookmark;
    private BookmarkAdapter mAdapter;
    private Cursor mCursor;
    private ListView mListView;
    private TextView mEmptyView;

    private static final int MENU_DELETE_ALL = 1;
    private static final int MENU_DELETE_ONE = 2;
    private static final int MENU_EDIT = 3;

    public static final String KEY_LOGO_BITMAP = "logo-bitmap";

    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.bookmark);

        Bitmap logo = getIntent().getParcelableExtra(KEY_LOGO_BITMAP);
        if (logo != null) {
            getActionBar().setLogo(new BitmapDrawable(getResources(), logo));
        }

        mListView = (ListView) findViewById(android.R.id.list);
        mEmptyView = (TextView) findViewById(android.R.id.empty);

        mBookmark = new BookmarkEnhance(this);
        mCursor = mBookmark.query();
        mAdapter = new BookmarkAdapter(this, R.layout.bookmark_item, null, new String[] {},
                new int[] {});
        mListView.setEmptyView(mEmptyView);
        mListView.setAdapter(mAdapter);
        mAdapter.changeCursor(mCursor);

        mListView.setOnItemClickListener(this);
        registerForContextMenu(mListView);
    }

    @Override
    protected void onStart() {
        super.onStart();
    }

    @Override
    protected void onResume() {
        super.onResume();
    }

    @Override
    protected void onPause() {
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        if (mAdapter != null) {
            mAdapter.changeCursor(null);
        }
        super.onDestroy();
    }

    @Override
    public boolean onCreateOptionsMenu(final Menu menu) {
        super.onCreateOptionsMenu(menu);
        menu.add(0, MENU_DELETE_ALL, 0, R.string.delete_all)
                .setIcon(android.R.drawable.ic_menu_delete);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(final MenuItem item) {
        switch (item.getItemId()) {
            case MENU_DELETE_ALL:
                mBookmark.deleteAll();
                return true;
            default:
                break;
        }
        return super.onOptionsItemSelected(item);
    }

    private class BookmarkAdapter extends SimpleCursorAdapter {

        public BookmarkAdapter(final Context context, final int layout, final Cursor c,
                final String[] from, final int[] to) {
            super(context, layout, c, from, to);
        }

        @Override
        public View newView(final Context context, final Cursor cursor, final ViewGroup parent) {
            final View view = super.newView(context, cursor, parent);
            final ViewHolder holder = new ViewHolder();
            holder.mTitleView = (TextView) view.findViewById(R.id.title);
            holder.mDataView = (TextView) view.findViewById(R.id.data);
            view.setTag(holder);
            return view;
        }

        @Override
        public void bindView(final View view, final Context context, final Cursor cursor) {
            final ViewHolder holder = (ViewHolder) view.getTag();
            holder.mId = cursor.getLong(BookmarkEnhance.INDEX_ID);
            holder.mTitle = cursor.getString(BookmarkEnhance.INDEX_TITLE);
            holder.mData = cursor.getString(BookmarkEnhance.INDEX_DATA);
            holder.mMimetype = cursor.getString(BookmarkEnhance.INDEX_MIME_TYPE);
            holder.mTitleView.setText(holder.mTitle);
            holder.mDataView.setText(holder.mData);
        }

        @Override
        public void changeCursor(final Cursor c) {
            super.changeCursor(c);
        }

    }

    private class ViewHolder {
        long mId;
        String mTitle;
        String mData;
        String mMimetype;
        TextView mTitleView;
        TextView mDataView;
    }

    @Override
    public void onItemClick(final AdapterView<?> parent, final View view, final int position,
            final long id) {
        final Object o = view.getTag();
        if (o instanceof ViewHolder) {
            final ViewHolder holder = (ViewHolder) o;
            finish();
            final Intent intent = new Intent(this, MovieActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
            String mime = "video/*";
            if (!(holder.mMimetype == null || "".equals(holder.mMimetype.trim()))) {
                mime = holder.mMimetype;
            }
            intent.setDataAndType(Uri.parse(holder.mData), mime);
            startActivity(intent);
        }
        if (LOG) {
            Log.v(TAG, "onItemClick(" + position + ", " + id + ")");
        }
    }

    @Override
    public void onCreateContextMenu(final ContextMenu menu, final View v,
            final ContextMenuInfo menuInfo) {
        super.onCreateContextMenu(menu, v, menuInfo);
        menu.add(0, MENU_DELETE_ONE, 0, R.string.delete);
        menu.add(0, MENU_EDIT, 0, R.string.edit);
    }

    @Override
    public boolean onContextItemSelected(final MenuItem item) {
        final AdapterContextMenuInfo info = (AdapterContextMenuInfo) item.getMenuInfo();
        switch (item.getItemId()) {
            case MENU_DELETE_ONE:
                mBookmark.delete(info.id);
                return true;
            case MENU_EDIT:
                final Object obj = info.targetView.getTag();
                if (obj instanceof ViewHolder) {
                    showEditDialog((ViewHolder) obj);
                } else {
                    Log.w(TAG, "wrong context item info " + info);
                }
                return true;
            default:
                return super.onContextItemSelected(item);
        }
    }

    private void showEditDialog(final ViewHolder holder) {
        if (LOG) {
            Log.v(TAG, "showEditDialog(" + holder + ")");
        }
        if (holder == null) {
            return;
        }
        final LayoutInflater inflater = LayoutInflater.from(this);
        final View v = inflater.inflate(R.layout.bookmark_edit_dialog, null);
        final EditText titleView = (EditText) v.findViewById(R.id.title);
        final EditText dataView = (EditText) v.findViewById(R.id.data);
        titleView.setText(holder.mTitle);
        dataView.setText(holder.mData);

        final AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(R.string.edit);
        builder.setView(v);
        builder.setIcon(R.drawable.ic_menu_display_bookmark);
        builder.setPositiveButton(android.R.string.ok, new OnClickListener() {

            @Override
            public void onClick(final DialogInterface dialog, final int which) {
                mBookmark.update(holder.mId, titleView.getText().toString(),
                        dataView.getText().toString(), 0);
            }

        });
        builder.setNegativeButton(android.R.string.cancel, null);
        final AlertDialog dialog = builder.create();
        dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE);
        dialog.setInverseBackgroundForced(true);
        dialog.show();
    }
}
