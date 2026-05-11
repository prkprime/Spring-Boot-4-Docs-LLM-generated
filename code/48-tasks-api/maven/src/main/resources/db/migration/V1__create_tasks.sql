create table task_item (
    id bigserial primary key,
    owner_subject varchar(120) not null,
    title varchar(160) not null,
    details text,
    status varchar(20) not null,
    created_at timestamptz not null,
    completed_at timestamptz
);

create index idx_task_item_owner_status on task_item(owner_subject, status);
