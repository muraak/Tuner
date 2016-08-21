package km.tool.kmtuner;

import android.util.Log;

import java.util.ArrayList;

/**
 * Created by Kenta on 2015/03/01.
 */
class PitchAnalyzer
{
    private double[] re;
    private double[] im;

    private int windowSize;

    public PitchAnalyzer(int window_size)
    {
        re = new double[window_size];
        im = new double[window_size];

        windowSize = window_size;
    }

    // key maxima 算出用のインナークラス(構造体として使用)
    private static class Maxima
    {
        public int    idx;   // tau
        public double value; // 信号に対するtauの相関の強さ

        Maxima()
        {
            idx = 0;
            value = 0;
        }
    }

    public void getExactPitch_hz(short[] audio_buffer, PitchData pitchData)
    {
        cast(audio_buffer, re, im);

        zeroPadding(re, im);

        //CAUTION: All elements of im must be set to zero.
        getExactPitch_hz(re, im, pitchData);
    }

    public void getExactPitch_hz(double [] sig_re,
                                        double [] sig_im,
                                        PitchData pitchData)
    {
        /**[ピッチのインデックス(周期)を算出]*/
        int pitch_tau = detectPitch(sig_re, sig_im);
        if(pitch_tau > 0)
        {
            /**[前後のインデックスを含む3つの周期を周波数に変換(関数のx軸に対応する)]*/
            double[] x_hz = {44100 / (double) (pitch_tau - 1), 44100 / (double) pitch_tau, 44100 / (double) (pitch_tau + 1)};
            /**[3点の相関値(関数のy軸に対応する)を配列にまとめる]*/
            double[] y_rel = {sig_re[pitch_tau - 1], sig_re[pitch_tau], sig_re[pitch_tau + 1]};

            /**[3点から放物線補完により補完後のピッチ(周波数)を算出]*/
            pitchData.setPitch(parabolicInterpolation(x_hz, y_rel));
            pitchData.setClarity(sig_re[pitch_tau]);
        }
    }

    private void cast(short[] record_buffer, double[] re, double[] im)
    {
        for(int i = 0; i < windowSize / 2; i++)
        {
            re[i] = (double) record_buffer[i];
            im[i] = 0;
        }
    }

    private void zeroPadding(double[] re, double[] im)
    {
        for(int i = windowSize /2; i < windowSize; i++)
        {
            re[i] = 0;
            im[i] = 0;
        }
    }

    public double parabolicInterpolation(double [] x, double [] y)
    {
        /**[3点より放物線補完したときの最大値(微分が0になる点)を算出]*/
        return (0.5f * ((x[1] * x[1] - x[0] * x[0]) * (y[1] - y[2]) - (x[1] * x[1] - x[2] * x[2]) * (y[1] - y[0]))
            / ((x[1] - x[0]) * (y[1] - y[2]) - (x[1] - x[2]) * (y[1] - y[0])));
    }

    // 処理窓に収められた信号からピッチ(の周期)を検出して返すメソッド
    // 注意：入力信号は保存されない
    // メソッド実行後のw_reには信号のNSDFが入っている
    // w_re[detectPitch(w_re, w_im)]はClarity(ピッチの確度，相関の強さ)を表す
    public int detectPitch(
            double [] w_re, // 入力信号実部(length = 処理窓サイズ)
            double [] w_im  // 入力信号虚部(length = 処理窓サイズ)
    )
    {
        int res;                 // ピッチと思われる周期の保存用
        nsdf(w_re, w_im);        // 信号のNSDFを算出
        res = peakPicking(w_re); // NSDFからピッチを検出
        return res;
    }

    // NSDFの系列(w)からピッチを検出するメソッド
    private int peakPicking(double [] w)
    {
        ArrayList<Maxima> maxima = new ArrayList<Maxima>(); // key maxima 格納用
        Maxima tmpMax = new Maxima();                  // 暫定max格納用
        boolean isInRange = false;                     // 測定区間かどうかを区別するための制御変数

        // key maxima のリストを算出
        for(int i = 1; i < w.length / 2; i++) // NSDFから使える値はw[1]～w[W/2]まで
        {
            if(isInRange)
            {
                if(w[i - 1] > 0 && w[i] <= 0)
                {
                    /* iが正から負へのゼロクロスポイント *
                     * だったときの処理                  */
                    maxima.add(tmpMax); // この区間のmax(tmpMax)をkey maximaに追加
                    isInRange = false;  // 測定区間から外れる
                }
                else
                {
                    // 区間内maxの選定
                    if(w[i] > tmpMax.value)
                    {
                        tmpMax.idx = i;
                        tmpMax.value = w[i];
                    }
                }
            }
            else
            {
                if(w[i - 1] < 0 && w[i] >= 0)
                {
                    /* iが負から正へのゼロクロスポイント *
                     * だったときの処理                  */
                    isInRange = true;
                }
            }
        }

        // key maximaの最大値から閾値を算出
        double threshold = 0; // 閾値の保存用
        // key maximaの中から最大値を選定
        for(int i = 0; i < maxima.size(); i++)
        {
            if(maxima.get(i).value > threshold)
            {
                threshold = maxima.get(i).value;
            }
        }
        // 閾値の算出(0.8～1.0の間で調整可能)
        threshold = threshold * 0.8;
        // 閾値を元にtau_pitchを算出 */
        int tau_pitch = 0;
        for(int i = 0; i < maxima.size(); i++)
        {
            if(maxima.get(i).value >= threshold)
            {
                tau_pitch = maxima.get(i).idx;
                break;
            }
        }

        return tau_pitch;
    }

    private void nsdf(
            double [] w_re,
            double [] w_im
    )
    {
        // 入力信号のNSDFを算出するメソッド
        // 注意：入力信号は保存されない(メソッド内で一時保存される)

        int WINDOW_SIZE = w_re.length;                 // 処理窓サイズ
        double [] signal_re = new double[w_re.length]; // 入力信号保存用

        // 入力信号をディープコピー
        System.arraycopy(w_re, 0, signal_re, 0, WINDOW_SIZE);

        // ACFを算出
        acf(w_re, w_im);
        // m_t(tau)を算出
        double m [] = new double[WINDOW_SIZE]; // m_t(tau)格納用
        m[0] = 2 * w_re[0];                    // 漸化式初期値
        for(int i = 1; i < WINDOW_SIZE; i++)   // 漸化式によりm[1]～m[WINDOW_SIZE - 1]を算出
        {
            m[i] = m[i - 1]
                    - Math.pow(signal_re[WINDOW_SIZE - i], 2)
                    - Math.pow(signal_re[i - 1], 2);
        }
        // NSDFを算出
        for(int i = 0; i < WINDOW_SIZE; i++)
        {
            if(m[i] != 0)
                w_re[i] = (2 * w_re[i]) / m[i];
            else
                Log.d("PitchAnalyzer.nsdf()", "zero division in calcNSDF()");
        }

        // w_reに入力信号のNSDFが算出されている
    }

    // 入力信号のACFを算出するメソッド
    // 注意：入力信号は保存されない
    private void acf(
            double [] w_re, // 入力信号実部(length = FFT窓サイズ)
            double [] w_im  // 入力信号虚部(length = FFT窓サイズ)
    )
    {
        // FFT
        fft(w_re, w_im, true/*FFT*/);
        // パワスペクトル密度を算出
        for(int i = 0; i < w_re.length; i++)
        {
            w_re[i] = w_re[i] * w_re[i] + w_im[i] * w_im[i]; // 絶対値2乗を算出
            w_im[i] = 0; // 結果は実数値となる
        }
        // IFFT
        // 逆フーリエ変換(ACF算出) <= ウィーナー・ヒンチンの定理参照
        fft(w_re, w_im, false/*IFFT*/);

        // w_reに信号のACFが算出されている
    }

    // FFTまたはIFFTを行うメソッド
    private void fft(
            double [] w_re,   // 入力信号実部(length = FFT窓サイズ)
            double [] w_im,   // 入力信号虚部(length = FFT窓サイズ)
            boolean direction // FFT,IFFTを選択するための制御変数(true: FFT, false: IFFT)
    )
    {
        int n, m, r;
        int N = w_re.length;
        int NUMBER_OF_STAGE;
        int [] index;
        double a_real, a_imag;
        double b_real, b_imag;
        double c_real, c_imag;
        double real, imag;

		/* FFTの段数を算出 */
        NUMBER_OF_STAGE = log2(N);

		/* バタフライ計算 */
        for(int stage = 1; stage <= NUMBER_OF_STAGE; stage++)
        {
            for(int i = 0; i < Math.pow(2, stage - 1); i++)
            {
                for(int j = 0; j < Math.pow(2, NUMBER_OF_STAGE - stage); j++)
                {
                    n = (int) Math.pow(2, NUMBER_OF_STAGE - stage + 1) * i + j;
                    m = (int) Math.pow(2, NUMBER_OF_STAGE - stage) + n;
                    r = (int) Math.pow(2, stage - 1) * j;
                    a_real = w_re[n];
                    a_imag = w_im[n];
                    b_real = w_re[m];
                    b_imag = w_im[m];
                    if(direction)
                    {
                        c_real = Math.cos((2.0 * Math.PI * r) / N);  // FFT
                        c_imag = -Math.sin((2.0 * Math.PI * r) / N); // FFT
                    }
                    else
                    {
                        c_real = Math.cos((2.0 * Math.PI * r) / N); // IFFT
                        c_imag = Math.sin((2.0 * Math.PI * r) / N); // IFFT
                    }

                    if(stage < NUMBER_OF_STAGE)
                    {
                        w_re[n] = a_real + b_real;
                        w_im[n] = a_imag + b_imag;
                        w_re[m] = (a_real - b_real) * c_real - (a_imag - b_imag) * c_imag;
                        w_im[m] = (a_imag - b_imag) * c_real + (a_real - b_real) * c_imag;
                    }
                    else
                    {
                        w_re[n] = a_real + b_real;
                        w_im[n] = a_imag + b_imag;
                        w_re[m] = a_real - b_real;
                        w_im[m] = a_imag - b_imag;
                    }
                }
            }
        }

		/*インデックス用配列の初期化*/
        index = new int[N];
        for(int stage = 1; stage <= NUMBER_OF_STAGE; stage++)
        {
            for(int i = 0; i < Math.pow(2, stage - 1); i++)
            {
                index[(int)Math.pow(2, stage - 1) + i] = index[i] + (int)Math.pow(2, NUMBER_OF_STAGE - stage);
            }
        }

		/* インデックス並べ替え */
        for(int k = 0; k < N; k++)
        {
            if(index[k] > k)
            {
                real = w_re[index[k]];
                imag = w_im[index[k]];
                w_re[index[k]] = w_re[k];
                w_im[index[k]] = w_im[k];
                w_re[k] = real;
                w_im[k] = imag;
            }
        }

        if(!direction)
        {
            /* IFFT時の処理 *
             * 1/Nで除算    */
            for (int i = 0; i < N; i++)
            {
                w_re[i] = w_re[i] / N;
                w_im[i] = w_im[i] / N;
            }
        }
    }

    private int log2(int x)
    {
        double result;

        // 底の変換公式を利用
        result = Math.log(x) / Math.log(2);

        return (int) result;
    }
}

class PitchData
{
    private double pitch = 0;
    private double clarity = 0;

    public void setPitch(double pitch)
    {
        this.pitch = pitch;
    }

    public void setClarity(double clarity)
    {
        this.clarity = clarity;
    }

    public double getPitch()
    {
        return pitch;
    }

    public double getClarity()
    {
        return clarity;
    }
}
