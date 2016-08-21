package km.tool.kmtuner;

/**
 * Created by Kenta on 2016/07/20.
 */
enum SharedData
{
    INSTANCE;

    private PitchData pitchData;

    public PitchData getPitchData()
    {
        return pitchData;
    }

    public void setPitchData(PitchData pitchData)
    {
        this.pitchData = pitchData;
    }
}
