package com.portfolio.domain.exception;

public interface Errors {

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
    }

    interface MarketData {
        String errorCode = "06";

        Error INVALID_INPUT = new Error(errorCode + "01");
        Error PRICE_NOT_FOUND = new Error(errorCode + "02");
        Error API_ERROR = new Error(errorCode + "03");
    }

}
