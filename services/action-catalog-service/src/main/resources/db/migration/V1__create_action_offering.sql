create table action_offering (
    id               varchar(64)  primary key,
    tenant_id        varchar(64)  not null,
    name             varchar(255) not null,
    description      varchar(2000),
    category         varchar(255),
    lifecycle_status varchar(32)  not null,
    channels         text,
    business_value   double precision not null,
    valid_from       timestamp with time zone,
    valid_to         timestamp with time zone,
    propensity_model text,
    version          bigint not null,
    last_update      timestamp with time zone not null
);

create index action_offering_tenant on action_offering (tenant_id, lifecycle_status);
