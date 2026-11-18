# Migration notes

## Adaptation

This change preserves existing delivery packages and tables, changes Maven artifact metadata to SathiRoute, targets Java 17, updates the container base images, and adds Flyway migrations for partner/service booking and customer booking management. Customer and partner APIs bind accounts to the authenticated JWT subject. Existing environment variable names and internal `com.routemesh` packages remain for compatibility.

## Verification limitations

The supplied workspace has no `.git` directory, no license file, and no original commit history or remote metadata. It is therefore not possible here to identify the original license or verify ownership/permission to relicense upstream code. No proprietary `LICENSE` was created and existing source attribution was left intact. SathiHomeCare ownership notices apply only to newly authored contributions where ownership is established; third-party terms continue to govern their code and dependencies. Before publishing, restore/inspect the authoritative repository history and obtain the correct licensing decision.

No Git history could be scanned for secrets. Tracked files in the available tree were searched for common credential terms; Compose uses environment substitutions and no concrete production credentials were identified. Treat this as a working-tree inspection only, not a history audit.

## Compatibility

The package and environment namespace `com.routemesh` / `ROUTEMESH_*` remains internal for now. Compose project/image/database identifiers also retain RouteMesh. Maven coordinates and Docker artifact naming changed, so external scripts that directly depend on `routemesh-*.jar` may need updating. Migrations V2, V3, and V4 are additive to delivery data; V4 backfills `appointment_date` from existing appointment timestamps before making it non-null. Test all migrations on a copy of the target database before production use.
