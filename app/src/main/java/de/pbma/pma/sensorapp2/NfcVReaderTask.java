package de.pbma.pma.sensorapp2;

import android.content.Context;
import android.media.AudioManager;
import android.nfc.Tag;
import android.nfc.tech.NfcV;
import android.os.AsyncTask;
import android.os.Vibrator;
import android.util.Log;
import android.widget.Toast;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

import de.pbma.pma.sensorapp2.SensorApp.MainActivity;
import de.pbma.pma.sensorapp2.model.GlucoseData;
import de.pbma.pma.sensorapp2.model.RawTagData;
import de.pbma.pma.sensorapp2.model.ReadingData;

import static android.content.Context.VIBRATOR_SERVICE;
import static android.media.AudioManager.RINGER_MODE_SILENT;
import static de.pbma.pma.sensorapp2.model.AlgorithmUtil.bytesToHexString;


public class NfcVReaderTask extends AsyncTask<Tag, Void, Boolean> {
    private static final String LOG_ID = "OpenLibre::" + NfcVReaderTask.class.getSimpleName();
    private static final long[] vibrationPatternSuccess = {0, 200, 100, 200}; // [ms]
    private static final long[] vibrationPatternFailure = {0, 500}; // [ms]
    private static final long nfcReadTimeout = 1000; // [ms]

    private MainActivity readActivity;
    private String sensorTagId;
    private byte[] data;

    public NfcVReaderTask(MainActivity readActivity) {
        this.readActivity = readActivity;
        data = new byte[360];
    }

    @Override
    protected void onPostExecute(Boolean success) {

        Vibrator vibrator = (Vibrator) readActivity.getSystemService(VIBRATOR_SERVICE);
        AudioManager audioManager = (AudioManager) readActivity.getSystemService(Context.AUDIO_SERVICE);

        if (!success) {
            Toast.makeText(readActivity,
                    readActivity.getResources().getString(R.string.reading_sensor_error),
                    Toast.LENGTH_SHORT
            ).show();

            if (audioManager.getRingerMode() != RINGER_MODE_SILENT) {
                vibrator.vibrate(vibrationPatternFailure, -1);
            }
            return;
        }

        if (audioManager.getRingerMode() != RINGER_MODE_SILENT) {
            vibrator.vibrate(vibrationPatternSuccess, -1);
        }

        if (RawTagData.getSensorReadyInMinutes(data) > 0) {
            Toast.makeText(readActivity,
                    readActivity.getResources().getString(R.string.reading_sensor_not_ready) + " " +
                            String.format(readActivity.getResources().getString(R.string.sensor_ready_in), RawTagData.getSensorReadyInMinutes(data)) + " " +
                            readActivity.getResources().getString(R.string.minutes),
                    Toast.LENGTH_LONG
            ).show();

            return;
        }

        Toast.makeText(readActivity,
                readActivity.getResources().getString(R.string.reading_sensor_success),
                Toast.LENGTH_SHORT
        ).show();

        readActivity.onNfcReadingFinished(processRawData(sensorTagId, data));
    }

    @Override
    protected Boolean doInBackground(Tag... params) {
        Tag tag = params[0];
        sensorTagId = bytesToHexString(tag.getId());
        return readNfcTag(tag);
    }

    private boolean readNfcTag(Tag tag) {
        NfcV nfcvTag = NfcV.get(tag);
        Log.d(NfcVReaderTask.LOG_ID, "Attempting to read tag data");
        try {

            nfcvTag.connect();
            final byte[] uid = tag.getId();
            final int step = MainActivity.NFC_USE_MULTI_BLOCK_READ ? 3 : 1;
            final int blockSize = 8;

            for (int blockIndex = 0; blockIndex <= 40; blockIndex += step) {
                byte[] cmd;
                if (MainActivity.NFC_USE_MULTI_BLOCK_READ) {
                    cmd = new byte[]{0x02, 0x23, (byte) blockIndex, 0x02}; // multi-block read 3 blocks
                } else {
                    cmd = new byte[]{0x60, 0x20, 0, 0, 0, 0, 0, 0, 0, 0, (byte) blockIndex, 0};
                    System.arraycopy(uid, 0, cmd, 2, 8);
                }

                byte[] readData;
                Long startReadingTime = System.currentTimeMillis();
                while (true) {
                    try {
                        readData = nfcvTag.transceive(cmd);
                        break;
                    } catch (IOException e) {
                        if ((System.currentTimeMillis() > startReadingTime + nfcReadTimeout)) {
                            Log.e(NfcVReaderTask.LOG_ID, "tag read timeout");
                            return false;
                        }
                    }
                }

                if (MainActivity.NFC_USE_MULTI_BLOCK_READ) {
                    System.arraycopy(readData, 1, data, blockIndex * blockSize, readData.length - 1);
                } else {
                    readData = Arrays.copyOfRange(readData, 2, readData.length);
                    System.arraycopy(readData, 0, data, blockIndex * blockSize, blockSize);
                }

            }
            Log.d(NfcVReaderTask.LOG_ID, "Got NFC tag data");

        } catch (Exception e) {

            Log.i(NfcVReaderTask.LOG_ID, e.toString());
            return false;

        } finally {
            try {
                nfcvTag.close();
            } catch (Exception e) {
                Log.e(NfcVReaderTask.LOG_ID, "Error closing tag!");
            }
        }
        Log.d(NfcVReaderTask.LOG_ID, "Tag data reader exiting");
        return true;
    }


    public static ReadingData processRawData(String sensorTagId, byte[] data) {

        RawTagData rawTagData = new RawTagData(sensorTagId, data);

        ReadingData readingData = new ReadingData(rawTagData);

        return readingData;
    }
}
