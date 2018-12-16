package de.pbma.pma.sensorapp2.SensorApp;

import android.content.Context;
import android.util.Log;
import android.widget.TextView;

import com.github.mikephil.charting.charts.Chart;
import com.github.mikephil.charting.components.MarkerView;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.highlight.Highlight;
import com.github.mikephil.charting.utils.MPPointF;

import java.util.Date;
import java.util.concurrent.TimeUnit;

import de.pbma.pma.sensorapp2.R;

import static de.pbma.pma.sensorapp2.model.AlgorithmUtil.mFormatDateTime;
import static de.pbma.pma.sensorapp2.model.GlucoseData.formatValue;
import static de.pbma.pma.sensorapp2.model.GlucoseData.getDisplayUnit;
import static java.lang.Math.max;
import static java.lang.Math.min;

public class DateTimeMarkerView extends MarkerView {

    private TextView tvContent;
    private long mFirstDate;

    public DateTimeMarkerView(Context context, int layoutResource) {
        super(context, layoutResource);
        tvContent = (TextView) findViewById(R.id.tvContent);
    }

    public void setFirstDate(long firstDate) {
        mFirstDate = firstDate;
    }

    private long convertXAxisValueToDate(float value) {
        return mFirstDate + (long) (value * TimeUnit.MINUTES.toMillis(1L));
    }

    @Override
    public void refreshContent(Entry e, Highlight highlight) {
        tvContent.setText(formatValue(e.getY()) + " " + getDisplayUnit() + " at " + mFormatDateTime.format(new Date(convertXAxisValueToDate(e.getX()))));
        super.refreshContent(e, highlight);
    }

    @Override
    public MPPointF getOffset() {
        return new MPPointF(-0.5f * getWidth(), -1.5f * getHeight());
    }

    @Override
    public MPPointF getOffsetForDrawingAtPoint(float posX, float posY) {
        MPPointF offset = getOffset();

        offset.x = max(offset.x, - posX);
        offset.y = max(offset.y, - posY);

        Chart chart = getChartView();
        if (chart != null) {
            offset.x = min(offset.x, chart.getWidth() - posX - getWidth());
            offset.y = min(offset.y, chart.getHeight() - posY - getHeight());
        }

        return offset;
    }
}