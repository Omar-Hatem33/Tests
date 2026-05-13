package com.team21.uber.payment.dto;

public class PaymentRequestDTO {
    private String method;
    private String cardLastFour;

    public String getMethod() { return method; }
    public void setMethod(String method) { this.method = method; }

    public String getCardLastFour() { return cardLastFour; }
    public void setCardLastFour(String cardLastFour) { this.cardLastFour = cardLastFour; }
}