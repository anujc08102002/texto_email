"use client";

import { useEffect, useRef, useState } from "react";

type State<T> = { loading: boolean; data?: T; error?: string };

export function useAdminResource<T>(loader: () => Promise<T>, dependencies: readonly unknown[] = []) {
  const [revision, setRevision] = useState(0);
  const [state, setState] = useState<State<T>>({ loading: true });
  const loaderRef = useRef(loader);
  useEffect(() => { loaderRef.current = loader; }, [loader]);
  const dependencyKey = JSON.stringify(dependencies);
  useEffect(() => {
    let active = true;
    Promise.resolve().then(() => {
      if (active) setState((current) => ({ ...current, loading: true, error: undefined }));
      return loaderRef.current();
    }).then((data) => active && setState({ loading: false, data })).catch((error: unknown) => {
        if (active) setState({ loading: false, error: error instanceof Error ? error.message : "Request failed" });
      });
    return () => { active = false; };
  }, [dependencyKey, revision]);
  return { ...state, reload: () => setRevision((value) => value + 1) };
}
