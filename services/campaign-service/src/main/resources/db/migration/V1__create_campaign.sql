create table campaign (
    id                      varchar(64)  primary key,
    tenant_id               varchar(64)  not null,
    name                    varchar(255) not null,
    description             varchar(2000),
    lifecycle_status        varchar(32)  not null,
    valid_from              timestamp with time zone,
    valid_to                timestamp with time zone,
    channels                text,
    eligibility_rule        varchar(4000),
    control_group_percent   double precision not null,
    max_impressions_per_day integer not null,
    actions                 text,
    version                 bigint not null,
    last_update             timestamp with time zone not null
);

create index campaign_tenant on campaign (tenant_id, lifecycle_status);
