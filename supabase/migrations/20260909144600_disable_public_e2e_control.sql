-- Test fixtures must not be provisioned through an anonymous production RPC.
revoke all on function public.e2e_test_control(text,text) from public, anon, authenticated;
