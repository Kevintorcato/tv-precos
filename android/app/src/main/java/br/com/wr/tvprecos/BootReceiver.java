package br.com.wr.tvprecos;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Reabre o painel automaticamente quando a TV box termina de ligar. */
public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
            Intent i = new Intent(context, MainActivity.class);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(i);
        }
    }
}
