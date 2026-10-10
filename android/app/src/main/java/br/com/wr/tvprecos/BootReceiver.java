package br.com.wr.tvprecos;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/**
 * Reabre o painel quando a box liga.
 * Escuta varios sinais de boot porque muita TV box barata nao manda o
 * BOOT_COMPLETED padrao (manda QUICKBOOT_POWERON ou similar). Aqui qualquer
 * um deles ja reabre o app.
 */
public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        try {
            Intent i = new Intent(context, MainActivity.class);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            context.startActivity(i);
        } catch (Exception ignore) {}
    }
}
