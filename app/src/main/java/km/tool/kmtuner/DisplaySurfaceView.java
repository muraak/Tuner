package km.tool.kmtuner;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

/**
 * Created by Kenta on 2016/07/17.
 */
public class DisplaySurfaceView extends SurfaceView
        implements Runnable, SurfaceHolder.Callback
{
    private SurfaceHolder mSurfaceHolder;

    private int mScreenWidth, mScreenHeight;

    private volatile boolean stopRequested = false;

    public DisplaySurfaceView(Context context)
    {
        super(context);
        mSurfaceHolder = getHolder();
        mSurfaceHolder.addCallback(this);
    }

    @Override /** The implementation of  SurfaceHolder.Callback */
    public void surfaceChanged(
            SurfaceHolder holder,
            int format,
            int width,
            int height)
    {
        mScreenWidth = width;
        mScreenHeight = height;
    }

    @Override /** The implementation of  SurfaceHolder.Callback */
    public void surfaceCreated(SurfaceHolder holder)
    {
        new Thread(this).start();
    }

    @Override /** The implementation of  SurfaceHolder.Callback */
    public void surfaceDestroyed(SurfaceHolder holder)
    {
        stopRequested = true;
        Tuner.INSTANCE.switchOFF();
        mSurfaceHolder.removeCallback(this);
    }

    @Override /** The implementation of Runnable */
    public void run()
    {
        draw();
    }

    private void draw()
    {
        Canvas canvas;

        Paint paint_bg = new Paint();
        paint_bg.setStyle(Paint.Style.FILL);
        paint_bg.setColor(Color.rgb(32, 32, 32));


        Paint paint_txt_pitch = new Paint();
        paint_txt_pitch.setColor(Color.rgb(64, 0, 0));
        paint_txt_pitch.setTextSize(96f);
        paint_txt_pitch.setTypeface(
                Typeface.createFromAsset(getContext().getAssets(),
                        "7-Segment-Display-Extended.ttf"));
        float pos_txt_pitch_x = (mScreenWidth / 2.0f)
                - (paint_txt_pitch.measureText("88") / 2.0f);
        float pos_txt_pitch_y = (mScreenHeight / 2.0f)
                - ((paint_txt_pitch.ascent() + paint_txt_pitch.descent()) / 2.0f);

        Paint paint_bmp_led_off = new Paint();
        paint_bmp_led_off.setAntiAlias(true);
        float pos_led_center_x = (mScreenWidth / 2.0f) - (dip2px(48.0f) / 2.0f);
        float pos_led_left_x = pos_led_center_x - dip2px(48f);
        float pos_led_right_x = pos_led_center_x + dip2px(48f);
        float pos_led_shared_y = (mScreenHeight / 2.0f) - dip2px(96.0f);

        Paint paint_txt_accidental = new Paint();
        paint_txt_accidental.setAntiAlias(true);
        paint_txt_accidental.setColor(Color.rgb(196, 196, 196));
        paint_txt_accidental.setTextSize(32f);
        float pos_flat_x = (mScreenWidth / 2.0f) -  (dip2px(48f) + paint_txt_accidental.measureText("♭") / 2.0f);
        float pos_sharp_x = (mScreenWidth / 2.0f) +  (dip2px(48f) - paint_txt_accidental.measureText("♯") / 2.0f);
        float pos_accidental_shared_y = (mScreenHeight / 2.0f) - dip2px(96.0f);


        long FRAME_RATE = 20;
        long FRAME_TIME = 1000 / FRAME_RATE;
        long start_time;
        long wait_time;

        Tuner.PitchDataForUI pitch_data;

        LEDBitmap led_bitmap = new LEDBitmap();

        Tuner.INSTANCE.switchON();

        while(!stopRequested)
        {
            /** CAUTION: Do not "new" the object in this while loop. */

            try
            {
                start_time = System.currentTimeMillis();

                pitch_data = Tuner.INSTANCE.getPitchDateForUI();

                canvas = mSurfaceHolder.lockCanvas();

                {
                    /** Draw Background. */
                    canvas.drawRect(
                            0, 0,
                            mScreenWidth, mScreenHeight,
                            paint_bg);
                }

                {
                    /** Draw text. */
                    canvas.drawText(
                            Tuner.GuitarPitch.OFF.toString(),
                            pos_txt_pitch_x, pos_txt_pitch_y,
                            paint_txt_pitch);

                    if(pitch_data.pitch != Tuner.GuitarPitch.OFF)
                    {
                        paint_txt_pitch.setColor(Color.rgb(238, 0, 0));
                        canvas.drawText(
                                pitch_data.pitch.toString(),
                                pos_txt_pitch_x, pos_txt_pitch_y,
                                paint_txt_pitch);
                        paint_txt_pitch.setColor(Color.rgb(64, 0, 0));
                    }

                    canvas.drawText(
                            "♭",
                            pos_flat_x, pos_accidental_shared_y,
                            paint_txt_accidental);
                    canvas.drawText(
                            "♯",
                            pos_sharp_x, pos_accidental_shared_y,
                            paint_txt_accidental);
                }

                {

                    /** Draw LED. */
                    canvas.drawBitmap(led_bitmap.getLEDBitmap(pitch_data.diff, LEDPosition.CENTER),
                        pos_led_center_x, pos_led_shared_y,
                            paint_bmp_led_off);
                    canvas.drawBitmap(led_bitmap.getLEDBitmap(pitch_data.diff, LEDPosition.LEFT),
                        pos_led_left_x, pos_led_shared_y,
                        paint_bmp_led_off);
                    canvas.drawBitmap(led_bitmap.getLEDBitmap(pitch_data.diff, LEDPosition.RIGHT),
                        pos_led_right_x, pos_led_shared_y,
                        paint_bmp_led_off);
                }



                mSurfaceHolder.unlockCanvasAndPost(canvas);

                wait_time = FRAME_TIME - (System.currentTimeMillis() - start_time);

                if(wait_time > 0)
                {
                    Thread.sleep(wait_time);
                }
            }
            catch (Exception e)
            {
                e.printStackTrace();
            }
        }
    }

    private enum LEDPosition
    {
        LEFT,
        CENTER,
        RIGHT
    }

    private class LEDBitmap
    {
        private Bitmap led_off;
        private Bitmap led_red;
        private Bitmap led_green;

        public LEDBitmap()
        {
            led_off = BitmapFactory.decodeResource(
                    getContext().getResources(), R.drawable.led_off);
            led_red = BitmapFactory.decodeResource(
                    getContext().getResources(), R.drawable.led_red);
            led_green = BitmapFactory.decodeResource(
                    getContext().getResources(), R.drawable.led_green);

            int dip_48 = (int)dip2px(48f);
            led_off = getResizedBitmap(led_off, dip_48, dip_48);
            led_red = getResizedBitmap(led_red, dip_48, dip_48);
            led_green = getResizedBitmap(led_green, dip_48, dip_48);

        }

        private Bitmap getResizedBitmap(Bitmap bitmap, int new_width, int new_height)
        {
            int width = bitmap.getWidth();
            int height = bitmap.getHeight();

            float scale_x = (float) new_width / width;
            float scale_y = (float) new_height / height;

            Matrix matrix = new Matrix();

            matrix.postScale(scale_x, scale_y);

            return Bitmap.createBitmap(bitmap,
                    0, 0, width, height,
                    matrix, false);
        }

        public Bitmap getLEDBitmap(Tuner.Difference diff, LEDPosition pos)
        {
            if(diff == Tuner.Difference.OFF)
            {
                return led_off;
            }
            else if(diff == Tuner.Difference.ON_KEY)
            {
                if(pos == LEDPosition.CENTER)
                {
                    return led_green;
                }
                else
                {
                    return led_off;
                }
            }
            else if(diff == Tuner.Difference.LOW)
            {
                if(pos == LEDPosition.LEFT)
                {
                    return led_red;
                }
                else
                {
                    return led_off;
                }
            }
            else if(diff == Tuner.Difference.HIGH)
            {
                if(pos == LEDPosition.RIGHT)
                {
                    return led_red;
                }
                else
                {
                    return led_off;
                }
            }
            else if(diff == Tuner.Difference.LITTLE_LOW)
            {
                if(pos == LEDPosition.CENTER)
                {
                    return led_green;
                }
                else if(pos == LEDPosition.LEFT)
                {
                    return led_red;
                }
                else
                {
                    return led_off;
                }
            }
            else if(diff == Tuner.Difference.LITTLE_HIGH)
            {
                if(pos == LEDPosition.CENTER)
                {
                    return led_green;
                }
                else if(pos == LEDPosition.RIGHT)
                {
                    return led_red;
                }
                else
                {
                    return led_off;
                }
            }
            else
            {
                return null;
            }
        }
    }

    private float dip2px(float dip)
    {
        return getResources().getDisplayMetrics().density * dip;
    }

    private  float px2dip(float px)
    {
        return (px / getResources().getDisplayMetrics().density);
    }

}
