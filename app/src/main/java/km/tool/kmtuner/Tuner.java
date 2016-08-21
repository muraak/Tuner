package km.tool.kmtuner;

import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.util.Log;

/**
 * Created by Kenta on 2016/07/20.
 */
public enum  Tuner implements Runnable
{
    INSTANCE; /** This is the sole object of this class */

    private static final int SAMPLING_RATE = 44100;
    public static final int WINDOW_SIZE = 4096;

    public static final double CLARITY_THRESHOLD = 0.75;

    private volatile boolean stopRequested = false;

    public enum Difference
    {
        OFF,
        ON_KEY,
        LITTLE_HIGH,
        LITTLE_LOW,
        HIGH,
        LOW
    }

    public enum GuitarPitch
    {
        OFF("88", 0),
        SIXTH("6E", 82.4),
        FIFTH("5A", 110.0),
        FOURTH("4D", 146.0),
        THIRD("3G", 196.0),
        SECOND("2B", 246.9),
        FIRST("1E", 329.6);

        private final String text;
        private final double hz;

        private static final double WIDE_RANGE_HZ = 10.0;
        private static final double MID_RANGE_HZ = 2.0;
        private static final double NARROW_RANGE_HZ = 1.0;

        private GuitarPitch(final String text, final double hz)
        {
            this.text = text;
            this.hz = hz;
        }

        @Override
        public String toString()
        {
            return text;
        }

        public double hz()
        {
            return hz;
        }

        public double hzHigh()
        {
            return (hz + WIDE_RANGE_HZ);
        }

        public double hzMidHigh()
        {
            return (hz + MID_RANGE_HZ);
        }

        public double hzNarrowHigh()
        {
            return (hz + NARROW_RANGE_HZ);
        }

        public double hzLow()
        {
            return (hz - WIDE_RANGE_HZ);
        }

        public double hzMidLow()
        {
            return (hz - MID_RANGE_HZ);
        }

        public double hzNarrowLow()
        {
            return (hz - NARROW_RANGE_HZ);
        }
    }

    public class PitchDataForUI
    {
        public Difference diff = Difference.OFF;
        public GuitarPitch pitch = GuitarPitch.OFF;

        public PitchDataForUI(Difference diff, GuitarPitch pitch)
        {
            this.diff = diff;
            this.pitch = pitch;
        }
    }

    public void switchON()
    {
        new Thread(INSTANCE).start();
    }

    public void switchOFF()
    {
        stopRequested = true;
    }

    @Override /** The implementation of Runnable. */
    public void run()
    {
        int buffer_size = AudioRecord.getMinBufferSize(
                SAMPLING_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT);

        buffer_size *= 4;

        Log.d(AppInfo.NAME.toString(), "buffer_size: " + String.valueOf(buffer_size * 4));

        AudioRecord audio_record = new AudioRecord(
                MediaRecorder.AudioSource.MIC,
                SAMPLING_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                SAMPLING_RATE * 2);

        short [] record_buffer =  new short[SAMPLING_RATE];

        PitchAnalyzer pitch_analyzer = new PitchAnalyzer(WINDOW_SIZE);
        PitchData pitch_data = new PitchData();

        audio_record.startRecording();

        while(!stopRequested)
        {
            audio_record.read(
                    record_buffer,
                    0/*offset*/,
                    record_buffer.length);

            pitch_analyzer.getExactPitch_hz(record_buffer, pitch_data);

            synchronized (SharedData.INSTANCE)
            {
                SharedData.INSTANCE.setPitchData(pitch_data);
            }
        }

        audio_record.release();
    }

    /** memory leak! */
    public PitchDataForUI getPitchDateForUI()
    {
        double pitch;
        double clarity;

        synchronized (SharedData.INSTANCE)
        {
            pitch = SharedData.INSTANCE.getPitchData().getPitch();
            clarity = SharedData.INSTANCE.getPitchData().getClarity();
        }

        if(clarity < CLARITY_THRESHOLD)
        {
            return new PitchDataForUI(Difference.OFF, GuitarPitch.OFF);
        }
        else
        {
            for(GuitarPitch it : GuitarPitch.values())
            {
                /** Skip the "GuitarPitch.OFF". */
                if(it.equals(GuitarPitch.OFF))
                    continue;

                if(pitch >= it.hz())
                {
                    if(pitch <= it.hzNarrowHigh())
                        return new PitchDataForUI(Difference.ON_KEY, it);
                    if(pitch <= it.hzMidHigh())
                        return new PitchDataForUI(Difference.LITTLE_HIGH, it);
                    if(pitch <= it.hzHigh())
                        return new PitchDataForUI(Difference.HIGH, it);
                }
                else
                {
                    if(pitch >= it.hzNarrowLow())
                        return new PitchDataForUI(Difference.ON_KEY, it);
                    if(pitch <= it.hzMidLow())
                        return new PitchDataForUI(Difference.LITTLE_LOW, it);
                    if(pitch <= it.hzLow())
                        return new PitchDataForUI(Difference.LOW, it);
                }
            }

            return new PitchDataForUI(Difference.OFF, GuitarPitch.OFF);
        }


    }

    private void genSine(double hz, short[] output)
    {
        double amplitude = 0.1 * (double) Short.MAX_VALUE;

        double[] tmp = new double[output.length];

        for(int i = 0; i < output.length; i++)
        {
            tmp[i] = amplitude * Math.sin(2 * Math.PI * hz * ((double)i / 44100));
            output[i] = (short) tmp[i];
        }
    }

    private void genSine(double hz, double[] output)
    {
        double amplitude = 0.1;

        for(int i = 0; i < output.length; i++)
        {
            output[i] = amplitude * Math.sin(2.0 * Math.PI * hz * ((double)i / 44100.0));
        }
    }
}
