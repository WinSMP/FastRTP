# How to Contribute

## General workflow

0. (External contributors only) Create a fork of the repository
1. Pull any changes from `main` to make sure you're up-to-date
2. Create a branch from `main`
    * Give your branch a name that describes your change (e.g. add-scoreboard)
    * Focus on one change per branch
3. Commit your changes
    * Keep your commits small and focused
    * Write descriptive commit messages in [Conventional Commit](https://www.conventionalcommits.org/en/v1.0.0/) format
    * Keep your lines of code to about ~110 characters of length, unless it's a constant string (like a sentence)
    * Keep your commit's body under 72 characters, and its title under 50
4. When you're ready, create a pull request to `main`
   * Keep your PRs small (preferably <300 LOC)
   * Format your title in [Conventional Commit](https://www.conventionalcommits.org/en/v1.0.0/) format
   * List any changes made in your description
   * Link any issues that your pull request is related to as well

### Example:
```text
feat(player): improve player experience by adding new feature

This commit introduces a new feature that enhances the player experience.
The feature is designed to be intuitive and easy to use, providing players
with a more enjoyable and engaging gameplay experience.

- Added a new command `/feature` that allows players to access the new feature.
- Implemented the core logic of the feature, including its functionality and behavior.
- Integrated the feature with the existing systems to ensure compatibility and stability.
```

After the pull request has been reviewed, approved, and passes all automated checks, it will be merged into the main branch.
