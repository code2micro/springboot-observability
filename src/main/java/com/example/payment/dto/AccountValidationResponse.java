package com.example.payment.dto;

public class AccountValidationResponse {

    private String accountId;
    private boolean valid;
    private long balance;

    public AccountValidationResponse() {
    }

    public AccountValidationResponse(String accountId, boolean valid, long balance) {
        this.accountId = accountId;
        this.valid = valid;
        this.balance = balance;
    }

    public String getAccountId() {
        return accountId;
    }

    public void setAccountId(String accountId) {
        this.accountId = accountId;
    }

    public boolean isValid() {
        return valid;
    }

    public void setValid(boolean valid) {
        this.valid = valid;
    }

    public long getBalance() {
        return balance;
    }

    public void setBalance(long balance) {
        this.balance = balance;
    }
}
