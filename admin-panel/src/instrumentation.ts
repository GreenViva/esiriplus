export async function register() {
  if (process.env.NEXT_RUNTIME === "nodejs") {
    const { Agent, setGlobalDispatcher } = await import("undici");
    setGlobalDispatcher(
      new Agent({
        connect: { timeout: 30_000 },
        keepAliveTimeout: 1_000,
        keepAliveMaxTimeout: 1_000,
        pipelining: 0,
      }),
    );
  }
}
