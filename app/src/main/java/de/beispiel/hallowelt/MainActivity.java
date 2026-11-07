package de.beispiel.hallowelt;

import android.app.Activity;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

public class MainActivity extends Activity {
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setGravity(Gravity.CENTER);
        int padding = (int) (32 * getResources().getDisplayMetrics().density);
        layout.setPadding(padding, padding, padding, padding);

        TextView message = new TextView(this);
        message.setTextSize(28);
        message.setGravity(Gravity.CENTER);
        message.setPadding(0, 0, 0, padding);
        if (savedInstanceState != null && savedInstanceState.getBoolean("greeted")) {
            message.setText("Hallo Welt");
        }
        Button button = new Button(this);
        button.setText("Klick mich");
        button.setOnClickListener(view -> message.setText("Hallo Welt"));
        layout.addView(message);
        layout.addView(button);
        setContentView(layout);
        greeting = message;
    }

    private TextView greeting;

    @Override
    protected void onSaveInstanceState(Bundle state) {
        state.putBoolean("greeted", greeting.getText().length() > 0);
        super.onSaveInstanceState(state);
    }
}
