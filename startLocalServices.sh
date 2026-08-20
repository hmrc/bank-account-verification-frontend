#!/usr/bin/env bash

sm2 --start BANK_ACCOUNT_VERIFICATION --appendArgs '{
    "BANK_ACCOUNT_REPUTATION":[
        "-Dauditing.enabled=false",
        "-Dmicroservice.services.access-control.enabled=false",
        "-Dmicroservice.services.eiscd.useLocal=true",
        "-Dmicroservice.services.modcheck.useLocal=true"
    ]
}'

sm2 --stop BANK_ACCOUNT_VERIFICATION_FRONTEND