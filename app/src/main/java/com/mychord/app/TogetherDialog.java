package com.mychord.app;

import android.app.Activity;
import android.content.Intent;
import android.text.InputFilter;
import android.text.InputType;
import android.view.Gravity;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

/** Menu "Dengar bersama" */
public class TogetherDialog {
    public static void show(Activity a, Runnable changed) {
        int m = ListenTogether.mode();
        if (m == ListenTogether.HOST) { showHost(a, changed); return; }
        if (m == ListenTogether.GUEST) {
            new MaterialAlertDialogBuilder(a)
                    .setTitle(R.string.tg_title)
                    .setMessage(a.getString(R.string.tg_in_session, ListenTogether.code()))
                    .setPositiveButton(R.string.tg_leave, (d, w) -> { ListenTogether.stop(); if (changed != null) changed.run(); })
                    .setNegativeButton(R.string.ok, null).show();
            return;
        }
        String[] opts = {a.getString(R.string.tg_start), a.getString(R.string.tg_join)};
        new MaterialAlertDialogBuilder(a)
                .setTitle(R.string.tg_title)
                .setItems(opts, (d, w) -> {
                    if (w == 0) {
                        ListenTogether.host(a);
                        if (changed != null) changed.run();
                        showHost(a, changed);
                    } else askCode(a, changed);
                })
                .setNegativeButton(R.string.cancel, null).show();
    }

    private static void showHost(Activity a, Runnable changed) {
        String code = ListenTogether.code();
        TextView tv = new TextView(a);
        tv.setText(code);
        tv.setTextSize(40);
        tv.setLetterSpacing(0.2f);
        tv.setGravity(Gravity.CENTER);
        tv.setTextColor(Ui.attr(a, R.attr.mcAccent));
        tv.setTypeface(tv.getTypeface(), android.graphics.Typeface.BOLD);
        TextView note = new TextView(a);
        note.setText(R.string.tg_host_note);
        note.setGravity(Gravity.CENTER);
        note.setTextColor(Ui.attr(a, R.attr.mcTextDim));
        LinearLayout box = new LinearLayout(a);
        box.setOrientation(LinearLayout.VERTICAL);
        int p = Ui.dp(a, 20);
        box.setPadding(p, p / 2, p, 0);
        box.addView(tv);
        box.addView(note);
        new MaterialAlertDialogBuilder(a)
                .setTitle(R.string.tg_title)
                .setView(box)
                .setPositiveButton(R.string.tg_invite, (d, w) -> {
                    Intent i = new Intent(Intent.ACTION_SEND);
                    i.setType("text/plain");
                    i.putExtra(Intent.EXTRA_TEXT, a.getString(R.string.tg_invite_text, code));
                    a.startActivity(Intent.createChooser(i, a.getString(R.string.tg_invite)));
                })
                .setNeutralButton(R.string.tg_end, (d, w) -> { ListenTogether.stop(); if (changed != null) changed.run(); })
                .setNegativeButton(R.string.ok, null).show();
    }

    private static void askCode(Activity a, Runnable changed) {
        EditText et = new EditText(a);
        et.setHint(R.string.tg_code_hint);
        et.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS);
        et.setFilters(new InputFilter[]{new InputFilter.LengthFilter(6), new InputFilter.AllCaps()});
        et.setGravity(Gravity.CENTER);
        et.setTextSize(28);
        LinearLayout box = new LinearLayout(a);
        int p = Ui.dp(a, 20);
        box.setPadding(p, p / 2, p, 0);
        box.addView(et, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        new MaterialAlertDialogBuilder(a)
                .setTitle(R.string.tg_join)
                .setView(box)
                .setPositiveButton(R.string.tg_join_btn, (d, w) -> {
                    String c = et.getText().toString().trim();
                    if (c.length() < 6) return;
                    ListenTogether.join(a, c);
                    Toast.makeText(a, a.getString(R.string.tg_joined, c.toUpperCase()), Toast.LENGTH_LONG).show();
                    if (changed != null) changed.run();
                })
                .setNegativeButton(R.string.cancel, null).show();
    }
}
