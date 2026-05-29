package com.example.bankbuddy;

import android.os.Bundle;
import android.view.MenuItem;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.bankbuddy.adapters.TransactionAdapter;
import com.example.bankbuddy.models.Models.*;
import com.google.gson.Gson;

/**
 * Activity to display transaction results and financial advice.
 */
public class ResultsActivity extends AppCompatActivity {

    private TextView adviceText, summaryText;
    private RecyclerView transactionsRecyclerView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_results);

        // Enable back button
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle("Financial Advice");
        }

        // Initialize views
        adviceText = findViewById(R.id.adviceText);
        summaryText = findViewById(R.id.summaryText);
        transactionsRecyclerView = findViewById(R.id.transactionsRecyclerView);

        // Get upload response from intent
        String responseJson = getIntent().getStringExtra("uploadResponse");
        if (responseJson == null) {
            Toast.makeText(this, "Error loading results", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        // Parse response
        UploadResponse uploadResponse = new Gson().fromJson(responseJson, UploadResponse.class);

        // Display summary
        summaryText.setText(String.format(
                "Found %d transactions • Processed in %.1f seconds",
                uploadResponse.transactionCount,
                uploadResponse.processingTime
        ));

        // Display advice
        adviceText.setText(uploadResponse.advice);

        // Set up transactions RecyclerView
        transactionsRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        TransactionAdapter adapter = new TransactionAdapter(uploadResponse.transactions);
        transactionsRecyclerView.setAdapter(adapter);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}
