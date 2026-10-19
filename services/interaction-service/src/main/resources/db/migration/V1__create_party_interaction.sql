create table party_interaction (
    id               varchar(64)  primary key,
    tenant_id        varchar(64)  not null,
    idempotency_key  varchar(128),
    party_id         varchar(128) not null,
    decision_id      varchar(64),
    offering_id      varchar(64)  not null,
    campaign_id      varchar(64),
    channel          varchar(64)  not null,
    outcome          varchar(16)  not null,
    interaction_date timestamp with time zone not null,
    constraint party_interaction_idempotency unique (tenant_id, idempotency_key)
);

create index party_interaction_party on party_interaction (tenant_id, party_id, interaction_date);
