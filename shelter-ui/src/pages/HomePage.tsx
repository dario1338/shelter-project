import { Button, Heading, Stack, Text } from "@chakra-ui/react";
import { Link as RouterLink } from "react-router-dom";
import type { AuthState } from "../auth/AuthContext";
import { useAuth } from "../auth/useAuth";

function describe(state: AuthState): string {
  switch (state.status) {
    case "loading":
      return "Loading…";
    case "anonymous":
      return "You are not signed in.";
    case "authenticated":
      return `Signed in as ${state.user.email}`;
  }
}

export default function HomePage() {
  const { state } = useAuth();
  return (
    <Stack gap="4">
      <Heading as="h1" size="2xl">
        Shelter
      </Heading>
      <Text>{describe(state)}</Text>
      <Button asChild alignSelf="start">
        <RouterLink to="/dogs">Browse dogs</RouterLink>
      </Button>
    </Stack>
  );
}
