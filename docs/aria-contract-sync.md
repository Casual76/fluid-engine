# Additive tool evidence contracts (embedded integration)

The 3.0.5 embedded engine retains its version and gains the canonical PampAI additive contracts.

`ToolOutput.result: ToolResultDetails? = null` carries ToolOutcome (ACCEPTED, COMPLETED, PARTIAL, UNCERTAIN, ERROR), JSON data, entity/operation/source references, coverage, freshness, pagination and typed retryable errors. Null remains legacy evidence. `AiTool.run(args, ctx, execution: ToolExecution)` defaults to the original two-argument overload; wrappers must forward it to preserve operation identity. `ToolArgumentValidator.error(args, schema)` validates before dispatch.

Bridge protocol negotiation is opt-in. Catalog without negotiation remains protocol 1, with legacy envelopes. Protocol 2 typed results require explicit capabilities. TypedResultCodec encodes/decodes evidence. AiToolHostProvider preserves signature admission, readiness and confirmations before dispatch; the requestId is forwarded as ToolExecution.operationId. Operation identity alone provides no durable deduplication guarantee; the source app owns reconciliation. See TypedProtocolTest and ToolContractsTest for compatibility and validation examples.

Only tool and bridge files were synchronized. Provider/model/orchestrator files are excluded, and no version/publication changes were made. Repository integration details and unrun verification commands: the app's docs/aria-data-access.md.
