package com.android.gallery3d.filtershow.controller;

import android.app.ActionBar.LayoutParams;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;

import com.android.gallery3d.R;
import com.android.gallery3d.filtershow.colorpicker.ColorListener;
import com.android.gallery3d.filtershow.colorpicker.ColorPickerDialog;
import com.android.gallery3d.filtershow.editors.Editor;

import java.util.Arrays;
import java.util.Vector;

public class ColorChooser implements Control {
    private final String LOGTAG = "StyleChooser";
    protected ParameterColor mParameter;
    protected LinearLayout mLinearLayout;
    protected Editor mEditor;
    private View mTopView;
    private Vector<Button> mIconButton = new Vector<Button>();
    protected int mLayoutID = R.layout.filtershow_control_color_chooser;
    Context mContext;
    private int mTransparent;
    private int mSelected;
    private static final int OPACITY_OFFSET = 3;
    private int[] mButtonsID = {
            R.id.draw_color_button01,
            R.id.draw_color_button02,
            R.id.draw_color_button03,
            R.id.draw_color_button04,
            R.id.draw_color_button05,
    };
    private Button[] mButton = new Button[mButtonsID.length];
    int[] mBasColors = {
            Color.RED & 0x80FFFFFF,
            Color.GREEN & 0x80FFFFFF,
            Color.BLUE & 0x80FFFFFF,
            Color.BLACK & 0x80FFFFFF,
            Color.WHITE & 0x80FFFFFF
    };
    int mSelectedButton = 0;

    @Override
    public void setUp(ViewGroup container, Parameter parameter, Editor editor) {
        container.removeAllViews();
        Resources res = container.getContext().getResources();
        mTransparent  = res.getColor(R.color.color_chooser_unslected_border);
        mSelected    = res.getColor(R.color.color_chooser_slected_border);
        mEditor = editor;
        mContext = container.getContext();
        mParameter = (ParameterColor) parameter;
        LayoutInflater inflater =
                (LayoutInflater) mContext.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        mTopView = inflater.inflate(mLayoutID, container, true);
        mLinearLayout = (LinearLayout) mTopView.findViewById(R.id.listStyles);
        mTopView.setVisibility(View.VISIBLE);

        mIconButton.clear();
        LayoutParams lp = new LayoutParams(120, 120);

        for (int i = 0; i < mButtonsID.length; i++) {
            final Button button = (Button) mTopView.findViewById(mButtonsID[i]);
            mButton[i] = button;
            float[] hsvo = new float[4];
            Color.colorToHSV(mBasColors[i], hsvo);
            hsvo[OPACITY_OFFSET] = (0xFF & (mBasColors[i] >> 24)) / (float) 255;
            button.setTag(hsvo);
            GradientDrawable sd = ((GradientDrawable) button.getBackground());

            sd.setColor(mBasColors[i]);
            sd.setStroke(3, (mSelectedButton == i) ? mSelected : mTransparent);

            final int buttonNo = i;
            button.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View arg0) {
                    selectColor(arg0, buttonNo);
                }
            });
        }
        Button button = (Button) mTopView.findViewById(R.id.draw_color_popupbutton);

        button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View arg0) {
                showColorPicker();
            }
        });

    }

    public void setColorSet(int[] basColors) {
        for (int i = 0; i < mBasColors.length; i++) {
            mBasColors[i] = basColors[i];
            float[] hsvo = new float[4];
            Color.colorToHSV(mBasColors[i], hsvo);
            hsvo[OPACITY_OFFSET] = (0xFF & (mBasColors[i] >> 24)) / (float) 255;
            mButton[i].setTag(hsvo);
            GradientDrawable sd = ((GradientDrawable) mButton[i].getBackground());
            sd.setColor(mBasColors[i]);
        }

    }

    public int[] getColorSet() {
        return mBasColors;
    }

    private void resetBorders() {
        for (int i = 0; i < mButtonsID.length; i++) {
            final Button button = mButton[i];

            GradientDrawable sd = ((GradientDrawable) button.getBackground());
            sd.setColor(mBasColors[i]);
            sd.setStroke(3, (mSelectedButton == i) ? mSelected : mTransparent);
        }
    }

    public void selectColor(View button, int buttonNo) {
        mSelectedButton = buttonNo;
        float[] hsvo = (float[]) button.getTag();
        mParameter.setValue(Color.HSVToColor((int) (hsvo[OPACITY_OFFSET] * 255), hsvo));
        resetBorders();
    }

    @Override
    public View getTopView() {
        return mTopView;
    }

    @Override
    public void setPrameter(Parameter parameter) {
        mParameter = (ParameterColor) parameter;
        updateUI();
    }

    @Override
    public void updateUI() {
        if (mParameter == null) {
            return;
        }
    }

    public void changeSelectedColor(float[] hsvo) {
        int c = Color.HSVToColor((int) (hsvo[3] * 255), hsvo);
        final Button button = mButton[mSelectedButton];

        GradientDrawable sd = ((GradientDrawable) button.getBackground());
        sd.setColor(c);
        mBasColors[mSelectedButton] = c;
        mParameter.setValue(Color.HSVToColor((int) (hsvo[OPACITY_OFFSET] * 255), hsvo));
        button.setTag(hsvo);
        button.invalidate();
    }

    public void showColorPicker() {
        ColorListener cl = new ColorListener() {
            @Override
            public void setColor(float[] hsvo) {
                changeSelectedColor(hsvo);
            }
        };
        ColorPickerDialog cpd = new ColorPickerDialog(mContext, cl);
        float[] c = (float[]) mButton[mSelectedButton].getTag();
        cpd.setColor(Arrays.copyOf(c, 4));
        cpd.show();
    }
}
