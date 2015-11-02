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

package com.android.gallery3d.mpo;

class MpoHeader {
    public static final short SOI =  (short) 0xFFD8;
    public static final short APP2 = (short) 0xFFE2;
    public static final short APP1 = (short) 0xFFE1;
    public static final short APP0 = (short) 0xFFE0;
    public static final short EOI = (short) 0xFFD9;

    /**
     *  SOF (start of frame). All value between SOF0 and SOF15 is SOF marker except for DHT, JPG,
     *  and DAC marker.
     */
    public static final short SOF0 = (short) 0xFFC0;
    public static final short SOF15 = (short) 0xFFCF;
    public static final short DHT = (short) 0xFFC4;
    public static final short JPG = (short) 0xFFC8;
    public static final short DAC = (short) 0xFFCC;

    public static final int APP2_FIELD_LENGTH_BYTES = 2;
    public static final int MP_FORMAT_IDENTIFIER_BYTES = 4;

    public static final boolean isSofMarker(short marker) {
        return marker >= SOF0 && marker <= SOF15 && marker != DHT && marker != JPG
                && marker != DAC;
    }
}
