package com.example.bankbuddy.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.bankbuddy.R;
import com.example.bankbuddy.models.Models.Transaction;

import java.util.List;

/**
 * Adapter for displaying transactions in a RecyclerView.
 */
public class TransactionAdapter extends RecyclerView.Adapter<TransactionAdapter.TransactionViewHolder> {

    private final List<Transaction> transactions;

    public TransactionAdapter(List<Transaction> transactions) {
        this.transactions = transactions;
    }

    @NonNull
    @Override
    public TransactionViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_transaction, parent, false);
        return new TransactionViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull TransactionViewHolder holder, int position) {
        Transaction transaction = transactions.get(position);
        holder.bind(transaction);
    }

    @Override
    public int getItemCount() {
        return transactions.size();
    }

    static class TransactionViewHolder extends RecyclerView.ViewHolder {
        private final TextView dateText;
        private final TextView descriptionText;
        private final TextView amountText;
        private final TextView balanceText;

        public TransactionViewHolder(@NonNull View itemView) {
            super(itemView);
            dateText = itemView.findViewById(R.id.transactionDate);
            descriptionText = itemView.findViewById(R.id.transactionDescription);
            amountText = itemView.findViewById(R.id.transactionAmount);
            balanceText = itemView.findViewById(R.id.transactionBalance);
        }

        public void bind(Transaction transaction) {
            dateText.setText(transaction.date);
            descriptionText.setText(transaction.description);
            amountText.setText(transaction.amount);
            balanceText.setText("Balance: " + transaction.balance);

            // Color amount based on positive/negative
            if (transaction.amount != null && transaction.amount.startsWith("-")) {
                amountText.setTextColor(0xFFD32F2F); // Red for debits
            } else {
                amountText.setTextColor(0xFF388E3C); // Green for credits
            }
        }
    }
}
