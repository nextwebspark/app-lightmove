-- A seat's node id stays unique per chart, but is checked at commit rather than per row. Hibernate rewrites the
-- @OrderColumn chart in place slot by slot, so removing or reordering a seat shifted the seats after it and held
-- one node id on two rows mid-write: V39's index refused it, and every later save of that chart was a 409.
DROP INDEX app_lm_position_org_node_uk;

ALTER TABLE app_lm_position_org_node
    ADD CONSTRAINT app_lm_position_org_node_uk UNIQUE (position_id, node_id) DEFERRABLE INITIALLY DEFERRED;
