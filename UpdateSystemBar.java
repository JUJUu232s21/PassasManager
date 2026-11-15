import android.app.Activity;
import android.content.Context;
import android.os.Build;
import android.view.WindowInsetsController;

import androidx.core.content.ContextCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsControllerCompat;

public class UpdateSystemBar {
    public static void Update(Activity activity){
        activity.getWindow().setNavigationBarColor(ContextCompat.getColor(activity, R.color.black));
        activity.getWindow().setStatusBarColor(ContextCompat.getColor(activity, R.color.black));

        WindowInsetsControllerCompat controller = WindowCompat.getInsetsController(activity.getWindow(), activity.getWindow().getDecorView());
        if (controller != null) {
            controller.setAppearanceLightStatusBars(false);
            controller.setAppearanceLightNavigationBars(false);
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            activity.getWindow().setNavigationBarContrastEnforced(false);
        }
    }
}
