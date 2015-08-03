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
import android.util.JsonReader;
import android.util.JsonWriter;

import com.android.gallery3d.filtershow.editors.EditorDualCamera;


public class FilterDualCamBasicRepresentation extends FilterBasicRepresentation {
    private static final String LOGTAG = "FilterDualCameraRepresentation";

    private static final String SERIAL_VALUE = "value";
    private static final String SERIAL_POINT = "point";

    private Point mPoint = new Point(-1, -1);

    public FilterDualCamBasicRepresentation(String name, int minVal, int defVal, int maxVal) {
        super(name, minVal, defVal, maxVal);
        setFilterType(FilterRepresentation.TYPE_DUALCAM);
        setFilterClass(ImageFilterDualCamera.class);
        setEditorId(EditorDualCamera.ID);
        setShowParameterValue(true);
    }

    @Override
    public FilterRepresentation copy() {
        FilterDualCamBasicRepresentation representation =
                new FilterDualCamBasicRepresentation(getName(), 0,0,0);
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
        if (a instanceof FilterDualCamBasicRepresentation) {
            FilterDualCamBasicRepresentation representation = (FilterDualCamBasicRepresentation) a;
            setPoint(representation.getPoint());
        }
    }

    @Override
    public boolean equals(FilterRepresentation representation) {
        if (!super.equals(representation)) {
            return false;
        }
        if (representation instanceof FilterDualCamBasicRepresentation) {
            FilterDualCamBasicRepresentation dualCam = (FilterDualCamBasicRepresentation) representation;
            if (dualCam.mPoint.equals(mPoint)) {
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

    @Override
    public String toString() {
        return "dualcam - value: " + getValue() + ", point: " + getPoint().toString();
    }

    // Serialization...

    public void serializeRepresentation(JsonWriter writer) throws IOException {
        writer.beginObject();
        {
            writer.name(NAME_TAG);
            writer.value(getName());
            writer.name(SERIAL_VALUE);
            writer.value(getValue());
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
            } else if (name.equalsIgnoreCase(SERIAL_VALUE)) {
                setValue(reader.nextInt());
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
