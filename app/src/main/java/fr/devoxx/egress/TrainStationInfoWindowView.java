package fr.devoxx.egress;

import android.content.Context;
import android.util.AttributeSet;
import android.widget.LinearLayout;
import android.widget.TextView;

import butterknife.ButterKnife;
import butterknife.InjectView;
import fr.devoxx.egress.model.Station;

public class TrainStationInfoWindowView extends LinearLayout {

    @InjectView(R.id.name) TextView nameView;
    @InjectView(R.id.owner) TextView ownerView;

    public TrainStationInfoWindowView(Context context) {
        super(context);
    }

    public TrainStationInfoWindowView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public TrainStationInfoWindowView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        ButterKnife.inject(this);
    }

    public void bind(Station station) {
        nameView.setText(station.getName());
        ownerView.setText(station.getOwner());
    }
}
