# Tickets

This directory is tracked with [Safanoria](https://github.com/mateuy-dev/safanoria):
one markdown file per ticket, `<id>.md`. The format is in the `SPEC.md` of the `safanoria` skill
(`~/.claude/skills/safanoria/`, or the project's `.claude/skills/safanoria/`).

- `_TEMPLATE.md`: template for new tickets; `_TEMPLATE.<type>.md` for one type.
- `attachments/<id>/`: files a ticket links to.

`safanoria-cli list`, `safanoria-cli board` and `safanoria-cli validate` show and check them.
