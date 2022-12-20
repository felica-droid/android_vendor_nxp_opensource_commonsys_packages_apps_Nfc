/*
 * Copyright (C) 2022 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.nfc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import android.bluetooth.BluetoothProtoEnums;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.nfc.Tag;
import android.os.Bundle;
import android.util.Log;
import androidx.test.core.content.pm.ApplicationInfoBuilder;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.dx.mockito.inline.extended.ExtendedMockito;
import com.android.nfc.handover.HandoverDataParser;

import java.util.concurrent.Executor;
import java.util.ArrayList;
import java.util.List;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.mockito.MockitoSession;

@RunWith(AndroidJUnit4.class)
public final class NfcReaderConflictOccurredTest {

    private static final String TAG = NfcReaderConflictOccurredTest.class.getSimpleName();
    private boolean mNfcSupported;

    private MockitoSession mStaticMockSession;
    private NfcDispatcher mNfcDispatcher;
    private boolean onLocked;

    @Before
    public void setUp() {
        mStaticMockSession = ExtendedMockito.mockitoSession()
                .mockStatic(NfcStatsLog.class)
                .startMocking();

        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        PackageManager mPackageManager = context.getPackageManager();
        if (!mPackageManager.hasSystemFeature(PackageManager.FEATURE_NFC_ANY)) {
            mNfcSupported = false;
            return;
        }
        mNfcSupported = true;

        ScreenStateHelper screenStateHelper = new ScreenStateHelper(context);
        if (screenStateHelper.checkScreenState() == ScreenStateHelper.SCREEN_STATE_ON_LOCKED) {
            onLocked = true;
            return;
        }
        onLocked = false;

        PackageManager mockPackageManager = Mockito.mock(PackageManager.class);
        // multiple resolveInfos for Tag
        when(mockPackageManager.queryIntentActivitiesAsUser(
                any(Intent.class),
                eq(0),
                anyInt())).thenReturn(constructConflictingResolveInfos());

        Context mockContext = new ContextWrapper(context) {
            @Override
            public PackageManager getPackageManager() {
                Log.i(TAG, "[Mock] getPackageManager");
                return mockPackageManager;
            }
        };

        InstrumentationRegistry.getInstrumentation().runOnMainSync(
              () -> mNfcDispatcher = new NfcDispatcher(
                      mockContext, new HandoverDataParser(), false));
        Assert.assertNotNull(mNfcDispatcher);
    }

    @After
    public void tearDown() {
        mStaticMockSession.finishMocking();
    }

    @Test
    public void testLogReaderConflict() {
        if (!mNfcSupported || onLocked) return;

        Tag tag = Tag.createMockTag(null, new int[0], new Bundle[0], 0L);
        mNfcDispatcher.dispatchTag(tag);
        ExtendedMockito.verify(() -> NfcStatsLog.write(
                NfcStatsLog.NFC_READER_CONFLICT_OCCURRED));
    }

    public List<ResolveInfo> constructConflictingResolveInfos() {
        List<ResolveInfo> mockResolves = new ArrayList<>();
        mockResolves.add(constructResolveInfo("appName1", "packageName1", 111));
        mockResolves.add(constructResolveInfo("appName2", "packageName2", 112));
        return mockResolves;
    }

    public ResolveInfo constructResolveInfo(String appName, String packageName, int uid) {
        ResolveInfo resolveInfo = new ResolveInfo();
        resolveInfo.activityInfo = new ActivityInfo();
        resolveInfo.activityInfo.applicationInfo =
            ApplicationInfoBuilder.newBuilder()
                .setName(appName)
                .setPackageName(packageName)
                .build();
        resolveInfo.activityInfo.applicationInfo.uid = uid;
        return resolveInfo;
    }
}
