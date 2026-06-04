package com.settle.domains.payment.infrastructure.persistence

import com.settle.domains.payment.application.usecase.PreparePaymentCommand

class PgMerchantAccountNotFoundException(
    command: PreparePaymentCommand,
) : RuntimeException(
        "Active PG merchant account not found for merchant '${command.merchantKey}' " +
            "and route '${command.pgProvider.code}:${command.pgProduct.code}:${command.pgMid}'",
    )
