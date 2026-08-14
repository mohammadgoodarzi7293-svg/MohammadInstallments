package com.mohammadgoudarzi.installments;

import android.app.Activity;
import android.os.Bundle;
import android.graphics.Color;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.widget.*;

import java.util.ArrayList;

public class MainActivity extends Activity {

    LinearLayout mainLayout;
    ArrayList<Installment> installments = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        buildMainScreen();
    }

    void buildMainScreen() {

        mainLayout = new LinearLayout(this);
        mainLayout.setOrientation(LinearLayout.VERTICAL);
        mainLayout.setPadding(30, 40, 30, 30);
        mainLayout.setBackgroundColor(Color.rgb(245, 247, 250));

        TextView title = new TextView(this);
        title.setText("مدیریت اقساط");
        title.setTextSize(28);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        title.setGravity(Gravity.CENTER);
        title.setTextColor(Color.rgb(30, 30, 30));

        mainLayout.addView(title,
                new LinearLayout.LayoutParams(-1, 100));

        Button addButton = new Button(this);
        addButton.setText("＋ افزودن قسط جدید");
        addButton.setTextSize(18);

        mainLayout.addView(addButton,
                new LinearLayout.LayoutParams(-1, 70));

        TextView summary = new TextView(this);
        summary.setText("\nهنوز قسطی ثبت نشده است.");
        summary.setTextSize(17);
        summary.setGravity(Gravity.CENTER);
        summary.setPadding(10, 30, 10, 30);

        mainLayout.addView(summary,
                new LinearLayout.LayoutParams(-1, -2));

        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);

        mainLayout.addView(list,
                new LinearLayout.LayoutParams(-1, 0, 1));

        addButton.setOnClickListener(v -> showAddInstallmentDialog(list, summary));

        setContentView(mainLayout);
    }

    void showAddInstallmentDialog(LinearLayout list, TextView summary) {

        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(20, 10, 20, 10);

        EditText name = new EditText(this);
        name.setHint("نام شخص / خرید");

        EditText amount = new EditText(this);
        amount.setHint("مبلغ هر قسط");
        amount.setInputType(2);

        EditText count = new EditText(this);
        count.setHint("تعداد اقساط");
        count.setInputType(2);

        box.addView(name);
        box.addView(amount);
        box.addView(count);

        new android.app.AlertDialog.Builder(this)
                .setTitle("افزودن قسط")
                .setView(box)
                .setNegativeButton("انصراف", null)
                .setPositiveButton("ثبت", (dialog, which) -> {

                    String n = name.getText().toString();
                    String a = amount.getText().toString();
                    String c = count.getText().toString();

                    if (n.isEmpty() || a.isEmpty() || c.isEmpty()) {
                        Toast.makeText(this,
                                "لطفاً همه موارد را وارد کنید",
                                Toast.LENGTH_SHORT).show();
                        return;
                    }

                    int amountValue = Integer.parseInt(a);
                    int countValue = Integer.parseInt(c);

                    Installment item =
                            new Installment(n, amountValue, countValue);

                    installments.add(item);

                    addInstallmentView(list, summary, item);

                    updateSummary(summary);
                })
                .show();
    }

    void addInstallmentView(LinearLayout list,
                            TextView summary,
                            Installment item) {

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(25, 20, 25, 20);
        card.setBackgroundColor(Color.WHITE);

        TextView info = new TextView(this);
        info.setText(
                item.name +
                "\nمبلغ هر قسط: " + item.amount +
                "\nتعداد اقساط: " + item.count +
                "\nپرداخت شده: 0"
        );

        info.setTextSize(17);
        info.setTextColor(Color.DKGRAY);

        card.addView(info);

        Button pay = new Button(this);
        pay.setText("✓ پرداخت قسط");

        card.addView(pay);

        pay.setOnClickListener(v -> {

            if (item.paid < item.count) {
                item.paid++;

                info.setText(
                        item.name +
                        "\nمبلغ هر قسط: " + item.amount +
                        "\nتعداد اقساط: " + item.count +
                        "\nپرداخت شده: " + item.paid
                );

                updateSummary(summary);

                if (item.paid == item.count) {
                    Toast.makeText(this,
                            "تمام اقساط این مورد پرداخت شد ✓",
                            Toast.LENGTH_LONG).show();
                }
            }
        });

        LinearLayout.LayoutParams params =
                new LinearLayout.LayoutParams(-1, -2);

        params.setMargins(0, 15, 0, 15);

        list.addView(card, params);
    }

    void updateSummary(TextView summary) {

        int total = 0;
        int paid = 0;

        for (Installment i : installments) {
            total += i.count;
            paid += i.paid;
        }

        summary.setText(
                "\nتعداد کل اقساط: " + total +
                "\nاقساط پرداخت شده: " + paid +
                "\nاقساط باقی‌مانده: " + (total - paid)
        );
    }

    static class Installment {

        String name;
        int amount;
        int count;
        int paid;

        Installment(String name, int amount, int count) {
            this.name = name;
            this.amount = amount;
            this.count = count;
            this.paid = 0;
        }
    }
}
