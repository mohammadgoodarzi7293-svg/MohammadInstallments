package com.mohammadgoudarzi.installments;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.*;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Locale;

public class MainActivity extends Activity {

    LinearLayout listLayout;
    TextView summaryText;
    ArrayList<Installment> items = new ArrayList<>();

    SharedPreferences prefs;

    int blue = Color.rgb(33, 150, 243);
    int green = Color.rgb(46, 125, 50);
    int red = Color.rgb(198, 40, 40);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        prefs = getSharedPreferences("installments_data", MODE_PRIVATE);

        loadData();
        buildScreen();
        refreshList();
    }

    void buildScreen() {

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(25, 30, 25, 20);
        root.setBackgroundColor(Color.rgb(245, 247, 250));

        TextView title = new TextView(this);
        title.setText("مدیریت اقساط");
        title.setTextSize(27);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        title.setTextColor(Color.rgb(25, 25, 25));
        title.setGravity(Gravity.CENTER);

        root.addView(title,
                new LinearLayout.LayoutParams(-1, 75));

        summaryText = new TextView(this);
        summaryText.setTextSize(16);
        summaryText.setGravity(Gravity.CENTER);
        summaryText.setPadding(10, 15, 10, 15);

        root.addView(summaryText);

        Button addButton = new Button(this);
        addButton.setText("＋ افزودن قسط جدید");
        addButton.setTextSize(17);
        addButton.setTextColor(Color.WHITE);
        addButton.setBackgroundColor(blue);

        root.addView(addButton,
                new LinearLayout.LayoutParams(-1, 65));

        ScrollView scroll = new ScrollView(this);

        listLayout = new LinearLayout(this);
        listLayout.setOrientation(LinearLayout.VERTICAL);
        listLayout.setPadding(0, 15, 0, 30);

        scroll.addView(listLayout);

        root.addView(scroll,
                new LinearLayout.LayoutParams(-1, 0, 1));

        addButton.setOnClickListener(v -> showAddDialog());

        setContentView(root);
    }

    void showAddDialog() {

        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(20, 5, 20, 5);

        EditText name = new EditText(this);
        name.setHint("نام شخص یا خرید");

        EditText total = new EditText(this);
        total.setHint("مبلغ کل");
        total.setInputType(InputType.TYPE_CLASS_NUMBER);

        EditText count = new EditText(this);
        count.setHint("تعداد اقساط");
        count.setInputType(InputType.TYPE_CLASS_NUMBER);

        box.addView(name);
        box.addView(total);
        box.addView(count);

        new AlertDialog.Builder(this)
                .setTitle("افزودن قرارداد")
                .setView(box)
                .setNegativeButton("انصراف", null)
                .setPositiveButton("ثبت", (dialog, which) -> {

                    if (name.getText().toString().trim().isEmpty()
                            || total.getText().toString().trim().isEmpty()
                            || count.getText().toString().trim().isEmpty()) {

                        Toast.makeText(this,
                                "لطفاً همه اطلاعات را وارد کنید",
                                Toast.LENGTH_SHORT).show();
                        return;
                    }

                    long totalAmount =
                            Long.parseLong(total.getText().toString());

                    int countValue =
                            Integer.parseInt(count.getText().toString());

                    if (countValue <= 0 || totalAmount <= 0) {
                        Toast.makeText(this,
                                "مبلغ و تعداد اقساط باید بیشتر از صفر باشد",
                                Toast.LENGTH_SHORT).show();
                        return;
                    }

                    Installment item = new Installment();

                    item.name = name.getText().toString();
                    item.total = totalAmount;
                    item.count = countValue;
                    item.paid = 0;

                    items.add(item);

                    saveData();
                    refreshList();
                })
                .show();
    }

    void refreshList() {

        listLayout.removeAllViews();

        long totalDebt = 0;
        long totalPaid = 0;
        int totalInstallments = 0;
        int paidInstallments = 0;

        for (Installment item : items) {

            totalDebt += item.total;

            long installmentAmount =
                    item.total / item.count;

            totalPaid += installmentAmount * item.paid;

            totalInstallments += item.count;
            paidInstallments += item.paid;

            addCard(item, installmentAmount);
        }

        long remaining = totalDebt - totalPaid;

        summaryText.setText(
                "کل بدهی: " + money(totalDebt) +
                "\nپرداخت شده: " + money(totalPaid) +
                "\nمانده: " + money(remaining) +
                "\nاقساط: " + paidInstallments +
                " از " + totalInstallments
        );
    }

    void addCard(Installment item, long installmentAmount) {

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(25, 20, 25, 20);
        card.setBackgroundColor(Color.WHITE);

        TextView name = new TextView(this);
        name.setText(item.name);
        name.setTextSize(20);
        name.setTypeface(Typeface.DEFAULT, Typeface.BOLD);

        TextView info = new TextView(this);
        info.setTextSize(15);
        info.setPadding(0, 10, 0, 10);

        updateInfo(info, item, installmentAmount);

        card.addView(name);
        card.addView(info);

        LinearLayout buttons = new LinearLayout(this);
        buttons.setOrientation(LinearLayout.HORIZONTAL);

        Button pay = new Button(this);
        pay.setText("✓ پرداخت قسط");

        Button edit = new Button(this);
        edit.setText("✏ ویرایش");

        Button delete = new Button(this);
        delete.setText("🗑 حذف");

        buttons.addView(pay,
                new LinearLayout.LayoutParams(0, 60, 1));

        buttons.addView(edit,
                new LinearLayout.LayoutParams(0, 60, 1));

        buttons.addView(delete,
                new LinearLayout.LayoutParams(0, 60, 1));

        card.addView(buttons);

        pay.setOnClickListener(v -> {

            if (item.paid < item.count) {

                item.paid++;

                saveData();
                updateInfo(info, item, installmentAmount);
                refreshList();

                Toast.makeText(this,
                        "قسط ثبت شد ✓",
                        Toast.LENGTH_SHORT).show();
            } else {

                Toast.makeText(this,
                        "تمام اقساط این مورد پرداخت شده",
                        Toast.LENGTH_SHORT).show();
            }
        });

        edit.setOnClickListener(v ->
                showEditDialog(item));

        delete.setOnClickListener(v -> {

            new AlertDialog.Builder(this)
                    .setTitle("حذف قرارداد")
                    .setMessage("آیا مطمئن هستید؟")
                    .setNegativeButton("خیر", null)
                    .setPositiveButton("بله", (d, w) -> {

                        items.remove(item);
                        saveData();
                        refreshList();
                    })
                    .show();
        });

        LinearLayout.LayoutParams params =
                new LinearLayout.LayoutParams(-1, -2);

        params.setMargins(0, 0, 0, 18);

        listLayout.addView(card, params);
    }

    void updateInfo(TextView info,
                    Installment item,
                    long installmentAmount) {

        long paidAmount =
                installmentAmount * item.paid;

        long remaining =
                item.total - paidAmount;

        info.setText(
                "مبلغ کل: " + money(item.total) +
                "\nمبلغ هر قسط: " + money(installmentAmount) +
                "\nتعداد اقساط: " + item.count +
                "\nپرداخت شده: " + item.paid +
                "\nباقی‌مانده: " + (item.count - item.paid) +
                "\nمانده بدهی: " + money(remaining)
        );
    }

    void showEditDialog(Installment item) {

        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(20, 5, 20, 5);

        EditText name = new EditText(this);
        name.setText(item.name);
        name.setHint("نام");

        EditText total = new EditText(this);
        total.setText(String.valueOf(item.total));
        total.setInputType(InputType.TYPE_CLASS_NUMBER);

        box.addView(name);
        box.addView(total);

        new AlertDialog.Builder(this)
                .setTitle("ویرایش")
                .setView(box)
                .setNegativeButton("انصراف", null)
                .setPositiveButton("ذخیره", (dialog, which) -> {

                    item.name = name.getText().toString();
                    item.total =
                            Long.parseLong(total.getText().toString());

                    saveData();
                    refreshList();
                })
                .show();
    }

    String money(long value) {

        return NumberFormat
                .getNumberInstance(Locale.US)
                .format(value) + " تومان";
    }

    void saveData() {

        try {

            JSONArray array = new JSONArray();

            for (Installment item : items) {

                JSONObject obj = new JSONObject();

                obj.put("name", item.name);
                obj.put("total", item.total);
                obj.put("count", item.count);
                obj.put("paid", item.paid);

                array.put(obj);
            }

            prefs.edit()
                    .putString("data", array.toString())
                    .apply();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    void loadData() {

        try {

            String data = prefs.getString("data", "");

            if (data.isEmpty())
                return;

            JSONArray array = new JSONArray(data);

            for (int i = 0; i < array.length(); i++) {

                JSONObject obj = array.getJSONObject(i);

                Installment item = new Installment();

                item.name = obj.getString("name");
                item.total = obj.getLong("total");
                item.count = obj.getInt("count");
                item.paid = obj.getInt("paid");

                items.add(item);
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    static class Installment {

        String name;
        long total;
        int count;
        int paid;
    }
}
