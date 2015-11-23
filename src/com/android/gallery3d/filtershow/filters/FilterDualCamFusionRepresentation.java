/*
 * Copyright (c) 2015, The Linux Foundation. All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are
 * met:
 *  * Redistributions of source code must retain the above copyright
 *    notice, this list of conditions and the following disclaimer.
 *  * Redistributions in binary form must reproduce the above
 *    copyright notice, this list of conditions and the following
 *    disclaimer in the documentation and/or other materials provided
 *    with the distribution.
 *  * Neither the name of The Linux Foundation nor the names of its
 *    contributors may be used to endorse or promote products derived
 *    from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED "AS IS" AND ANY EXPRESS OR IMPLIED
 * WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NON-INFRINGEMENT
 * ARE DISCLAIMED.  IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS
 * BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR
 * BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
 * WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE
 * OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN
 * IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package com.android.gallery3d.filtershow.filters;

import java.io.IOException;

import android.graphics.Point;
import android.net.Uri;
import android.util.JsonReader;
import android.util.JsonWriter;

import com.android.gallery3d.R;
import com.android.gallery3d.filtershow.editors.EditorDualCamFusion;


public class FilterDualCamFusionRepresentation extends FilterRepresentation {
    private static final String LOGTAG = "FilterFusionRepresentation";
    public static final String SERIALIZATION_NAME = "FUSION";

    private static final String SERIAL_UNDERLAY_IMAGE = "image";
    private static final String SERIAL_POINT = "point";

    private Point mPoint = new Point(-1, -1);
    private String mUri = "";

    public FilterDualCamFusionRepresentation() {
        super("Fusion");
        setSerializationName(SERIALIZATION_NAME);
        setFilterType(FilterRepresentation.TYPE_DUALCAM);
        setFilterClass(ImageFilterDualCamFusion.class);
        setEditorId(EditorDualCamFusion.ID);
        setShowParameterValue(false);
        setTextId(R.string.fusion);
        setOverlayId(R.drawable.fusion);
        setOverlayOnly(true);
    }

    @Override
    public FilterRepresentation copy() {
        FilterDualCamFusionRepresentation representation =
                new FilterDualCamFusionRepresentation();
        copyAllParameters(representation);
        return representation;
    }

    @Override
    protected void copyAllParameters(FilterRepresentation representation) {
        super.copyAllParameters(representation);
        representation.useParametersFrom(this);
    }

    public void useParametersFrom(FilterRepresentation a) {
        super.useParametersFrom(a);
        if (a instanceof FilterDualCamFusionRepresentation) {
            FilterDualCamFusionRepresentation representation = (FilterDualCamFusionRepresentation) a;
            setPoint(representation.getPoint());
            setUnderlay(representation.getUnderlay());
        }
    }

    @Override
    public boolean equals(FilterRepresentation representation) {
        if (!super.equals(representation)) {
            return false;
        }
        if (representation instanceof FilterDualCamFusionRepresentation) {
            FilterDualCamFusionRepresentation fusion = (FilterDualCamFusionRepresentation) representation;
            if (fusion.mPoint.equals(mPoint) &&
                    fusion.mUri.equals(mUri)) {
                return true;
            }
        }
        return false;
    }

    public void setPoint(int x, int y) {
        mPoint = new Point(x,y);
    }

    public void setPoint(Point point) {
        mPoint = point;
    }

    public Point getPoint() {
        return mPoint;
    }

    public void setUnderlay(Uri uri) {
        if(uri != null) {
            mUri = uri.toString();
        } else {
            mUri = "";
        }
    }

    public void setUnderlay(String uri) {
        if(uri != null)
            mUri = uri;
        else
            mUri = "";
    }

    public boolean hasUnderlay() {
        return (mUri != null) && (mUri.isEmpty() == false);
    }

    public String getUnderlay() {
        return mUri;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("fusion - underlay: ").append(getUnderlay());
        sb.append(", point: ").append(getPoint().toString());

        return sb.toString();
    }

    // Serialization...
    public void serializeRepresentation(JsonWriter writer) throws IOException {
        writer.beginObject();
        {
            writer.name(NAME_TAG);
            writer.value(getName());
            writer.name(SERIAL_UNDERLAY_IMAGE);
            writer.value(mUri);
            writer.name(SERIAL_POINT);
            writer.beginArray();
            writer.value(mPoint.x);
            writer.value(mPoint.y);
            writer.endArray();
        }
        writer.endObject();
    }

    public void deSerializeRepresentation(JsonReader reader) throws IOException {
        reader.beginObject();
        while (reader.hasNext()) {
            String name = reader.nextName();
            if (name.equalsIgnoreCase(NAME_TAG)) {
                setName(reader.nextString());
            } else if (name.equalsIgnoreCase(SERIAL_UNDERLAY_IMAGE)) {
                setUnderlay(reader.nextString());
            } else if (name.equalsIgnoreCase(SERIAL_POINT)) {
                reader.beginArray();
                reader.hasNext();
                mPoint.x = reader.nextInt();
                reader.hasNext();
                mPoint.y = reader.nextInt();
                reader.endArray();
            } else {
                reader.skipValue();
            }
        }
        reader.endObject();
    }
}
