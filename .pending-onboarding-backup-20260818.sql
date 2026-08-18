--
-- PostgreSQL database dump
--

\restrict EbcN3WIKdrL5Is4MuxdeRM7F6eAgADiZBMPRBd07myyVHUwGd4HT9yfitFXX9ey

-- Dumped from database version 16.13
-- Dumped by pg_dump version 18.4

SET statement_timeout = 0;
SET lock_timeout = 0;
SET idle_in_transaction_session_timeout = 0;
SET transaction_timeout = 0;
SET client_encoding = 'UTF8';
SET standard_conforming_strings = on;
SELECT pg_catalog.set_config('search_path', '', false);
SET check_function_bodies = false;
SET xmloption = content;
SET client_min_messages = warning;
SET row_security = off;

--
-- Data for Name: app_lm_pending_onboarding; Type: TABLE DATA; Schema: public; Owner: lm_app
--

COPY public.app_lm_pending_onboarding (id, user_id, name, company_size, primary_region, team_focus, job_title, invitations, expires_at, created_at, updated_at, version) FROM stdin;
a9c959fa-9755-4f48-8183-762b54c91df1	665699e6-544c-45aa-a248-74ee8da3d906	Ambrish Org	1–10 people	GCC	Executive search	\N	[{"role": "ADMIN", "email": "test1@ambrishai.com"}]	2026-07-18 15:49:08.321461+00	2026-07-17 15:49:08.473005+00	2026-07-17 15:49:45.673896+00	1
fa75c065-8f1f-45b5-9a74-10507267a904	49136dd0-fead-481c-9db3-ebea68957976	ABC	1–10 people	GCC	Executive search	\N	[]	2026-08-18 21:21:56.613361+00	2026-08-17 21:21:56.653681+00	2026-08-17 21:21:56.653687+00	0
\.


--
-- PostgreSQL database dump complete
--

\unrestrict EbcN3WIKdrL5Is4MuxdeRM7F6eAgADiZBMPRBd07myyVHUwGd4HT9yfitFXX9ey

