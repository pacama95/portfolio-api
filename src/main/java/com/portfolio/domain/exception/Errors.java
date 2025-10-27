package com.portfolio.domain.exception;

public interface Errors {

    interface Position {
        String errorCode = "01";

        Error INVALID_INPUT = new Error(errorCode + "01");
        Error OVERSELL = new Error(errorCode + "02");
        Error INVALID_TRANSACTION_REVERSAL = new Error(errorCode + "03");
    }

    interface GetPortfolioSummary {
        String errorCode = "10";

        Error INVALID_INPUT = new Error(errorCode + "01");
        Error NOT_FOUND = new Error(errorCode + "02");
        Error PERSISTENCE_ERROR = new Error(errorCode + "03");
    }

    interface GetPosition {
        String errorCode = "02";

        Error INVALID_INPUT = new Error(errorCode + "01");
        Error NOT_FOUND = new Error(errorCode + "02");
        Error PERSISTENCE_ERROR = new Error(errorCode + "03");
    }

    interface UpdateMarketData {
        String errorCode = "04";

        Error INVALID_INPUT = new Error(errorCode + "01");
        Error NOT_FOUND = new Error(errorCode + "02");
        Error PERSISTENCE_ERROR = new Error(errorCode + "03");
    }

    interface ProcessTransactionEvent {
        String errorCode = "05";

        Error INVALID_INPUT = new Error(errorCode + "01");
        Error PERSISTENCE_ERROR = new Error(errorCode + "02");
        Error CALCULATION_ERROR = new Error(errorCode + "03");
        Error UNEXPECTED_ERROR = new Error(errorCode + "04");
        Error ALREADY_PROCESSED = new Error(errorCode + "05");
        Error DUPLICATED_POSITION = new Error(errorCode + "06");
    }

    interface MarketData {
        String errorCode = "06";

        Error INVALID_INPUT = new Error(errorCode + "01");
        Error PRICE_NOT_FOUND = new Error(errorCode + "02");
        Error API_ERROR = new Error(errorCode + "03");
    }

}
