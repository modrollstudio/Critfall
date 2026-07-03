# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added

- Multiloader project scaffold: `common` + `neoforge` modules targeting NeoForge 1.21.1 (Java 21, ModDevGradle), Spotless formatting, GitHub Actions CI. (M0)
- Dice engine (`studio.modroll.critfall.dice`): expression parser and roller supporting `NdM+K`, keep-highest/lowest (`kh`/`kl`), advantage/disadvantage, multi-term expressions, min/max bounds, per-die roll breakdown, and fully injectable RNG. No Minecraft dependencies. See `docs/dice-expressions.md`. (M1)
